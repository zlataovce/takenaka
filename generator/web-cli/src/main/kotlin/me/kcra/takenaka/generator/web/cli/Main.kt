/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023 Matous Kucera
 *
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("Main")

package me.kcra.takenaka.generator.web.cli

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.*
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.LegacySpigotMappingPrepender
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.transformers.DeterministicMinifier
import me.kcra.takenaka.generator.web.transformers.Minifier
import me.kcra.takenaka.generator.web.transformers.Transformer
import mu.KotlinLogging
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * Minification options.
 */
enum class MinifierImpls {
    /**
     * Deterministic minification ([DeterministicMinifier]).
     */
    DETERMINISTIC,

    /**
     * Normal minification ([Minifier]).
     */
    NORMAL,

    /**
     * No minification.
     */
    NONE
}

/**
 * The application entrypoint.
 */
fun main(args: Array<String>) {
    val parser = ArgParser("web-cli")
    val output by parser.option(ArgType.String, shortName = "o", description = "Output directory").default("output")
    val version by parser.option(ArgType.String, shortName = "v", description = "Target Minecraft version, can be specified multiple times").multiple().required()
    val cache by parser.option(ArgType.String, shortName = "c", description = "Caching directory for mappings and other resources").default("cache")
    val strictCache by parser.option(ArgType.Boolean, description = "Enforces strict cache validation").default(false)
    val clean by parser.option(ArgType.Boolean, description = "Removes previous build output and cache before launching").default(false)

    // generator-specific settings below

    val minifier by parser.option(ArgType.Choice<MinifierImpls>(), shortName = "m", description = "The minifier implementation used for minifying the documentation").default(MinifierImpls.DETERMINISTIC)
    val javadoc by parser.option(ArgType.String, shortName = "j", description = "Javadoc site that should be referenced in the documentation, can be specified multiple times").multiple()
    val skipSynthetic by parser.option(ArgType.Boolean, description = "Excludes synthetic classes and class members from the documentation").default(true)

    parser.parse(args)

    val options = buildResolverOptions {
        if (!strictCache) {
            relaxedCache()
        }
    }

    val workspace = workspace {
        rootDirectory(output)
        resolverOptions = options
    }
    val cacheWorkspace = compositeWorkspace {
        rootDirectory(cache)
        resolverOptions = options
    }

    if (clean) {
        workspace.clean()
        cacheWorkspace.clean()
    }

    // generator setup below

    val objectMapper = objectMapper()
    val xmlMapper = XmlMapper()

    val transformers = mutableListOf<Transformer>()

    logger.info { "using minifier type $minifier" }
    when (minifier) {
        MinifierImpls.DETERMINISTIC -> {
            transformers += DeterministicMinifier()
        }
        MinifierImpls.NORMAL -> {
            transformers += Minifier()
        }
        else -> {}
    }

    val indexers = mutableListOf<ClassSearchIndex>(objectMapper.modularClassSearchIndexOf(JDK_17_BASE_URL))

    javadoc.mapTo(indexers) { javadocDef ->
        val javadocParams = javadocDef.split('+', limit = 2)

        when (javadocParams.size) {
            2 -> classSearchIndexOf(javadocParams[1], javadocParams[0])
            else -> objectMapper.modularClassSearchIndexOf(javadocParams[0])
        }
    }

    logger.info { "using ${indexers.size} javadoc indexer(s)" }

    val mappingsCache = cacheWorkspace.createCompositeWorkspace {
        this.name = "mappings"
    }
    val sharedCache = cacheWorkspace.createWorkspace {
        this.name = "shared"
    }

    val yarnProvider = YarnMetadataProvider(sharedCache, xmlMapper)
    val generator = WebGenerator(
        workspace,
        version,
        mappingsCache,
        { versionWorkspace ->
            val mojangProvider = MojangManifestAttributeProvider(versionWorkspace, objectMapper)
            val spigotProvider = SpigotManifestProvider(versionWorkspace, objectMapper)
            val _prependedClasses = mutableListOf<String>()

            listOf(
                MojangServerMappingResolver(versionWorkspace, mojangProvider),
                IntermediaryMappingResolver(versionWorkspace, sharedCache),
                YarnMappingResolver(versionWorkspace, yarnProvider),
                SeargeMappingResolver(versionWorkspace, sharedCache),
                WrappingContributor(SpigotClassMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                    // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
                    // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
                    LegacySpigotMappingPrepender(it, prependedClasses = _prependedClasses, prependEverything = versionWorkspace.version.id == "1.16.5")
                },
                WrappingContributor(SpigotMemberMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                    LegacySpigotMappingPrepender(it, prependedClasses = _prependedClasses)
                },
                VanillaMappingContributor(versionWorkspace, mojangProvider)
            )
        },
        skipSynthetic,
        // Searge adds their ID namespace sometimes, so don't perform any corrections on that
        VanillaMappingContributor.NAMESPACES + "searge_id",
        objectMapper,
        xmlMapper,
        transformers,
        listOf("mojang", "spigot", "yarn", "searge", "intermediary", "source"),
        mapOf(
            "mojang" to namespaceDescOf(
                "Mojang",
                "#4D7C0F",
                MojangServerMappingResolver.META_LICENSE
            ),
            "spigot" to namespaceDescOf(
                "Spigot",
                "#CA8A04",
                AbstractSpigotMappingResolver.META_LICENSE
            ),
            "yarn" to namespaceDescOf(
                "Yarn",
                "#626262",
                YarnMappingResolver.META_LICENSE
            ),
            "searge" to namespaceDescOf(
                "Searge",
                "#B91C1C",
                SeargeMappingResolver.META_LICENSE
            ),
            "intermediary" to namespaceDescOf(
                "Intermediary",
                "#0369A1",
                IntermediaryMappingResolver.META_LICENSE
            ),
            "source" to namespaceDescOf("Obfuscated", "#581C87")
        ),
        CompositeClassSearchIndex(indexers),
        listOf("spigot")
    )

    logger.info { "starting generator" }
    val time = measureTimeMillis {
        runBlocking {
            generator.generate()
        }
    }
    logger.info { "generator finished in ${time / 1000} second(s)" }
}
