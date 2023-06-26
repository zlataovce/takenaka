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
import me.kcra.takenaka.core.buildWorkspaceOptions
import me.kcra.takenaka.core.compositeWorkspace
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.analysis.impl.AnalysisOptions
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.analysis.impl.StandardProblemKinds
import me.kcra.takenaka.core.mapping.resolve.impl.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.workspace
import me.kcra.takenaka.generator.common.ResolvingMappingProvider
import me.kcra.takenaka.generator.common.buildMappingConfig
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.transformers.Minifier
import mu.KotlinLogging
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * Minification options.
 */
enum class MinifierImpls {
    /**
     * Deterministic minification.
     */
    DETERMINISTIC,

    /**
     * Normal minification.
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
    val noJoined by parser.option(ArgType.Boolean, description = "Don't cache joined mapping files").default(false)

    // generator-specific settings below

    val minifier by parser.option(ArgType.Choice<MinifierImpls>(), shortName = "m", description = "The minifier implementation used for minifying the documentation").default(MinifierImpls.NORMAL)
    val javadoc by parser.option(ArgType.String, shortName = "j", description = "Javadoc site that should be referenced in the documentation, can be specified multiple times").multiple()
    val synthetic by parser.option(ArgType.Boolean, shortName = "s", description = "Include synthetic classes and class members in the documentation").default(false)
    val noMeta by parser.option(ArgType.Boolean, description = "Don't emit HTML metadata tags in OpenGraph format").default(false)

    parser.parse(args)

    val options = buildWorkspaceOptions {
        if (!strictCache) {
            relaxedCache()
        }
    }

    val workspace = workspace {
        options(options)
        rootDirectory(output)
    }
    val cacheWorkspace = compositeWorkspace {
        options(options)
        rootDirectory(cache)
    }

    if (clean) {
        workspace.clean()
        cacheWorkspace.clean()
    }

    // generator setup below

    val objectMapper = objectMapper()
    val xmlMapper = XmlMapper()

    val mappingsCache = cacheWorkspace.createCompositeWorkspace {
        name = "mappings"
    }
    val sharedCache = cacheWorkspace.createWorkspace {
        name = "shared"
    }

    val yarnProvider = YarnMetadataProvider(sharedCache, xmlMapper)
    val mappingConfig = buildMappingConfig {
        version(version)
        workspace(mappingsCache)

        // remove Searge's ID namespace, it's not necessary
        intercept { v ->
            NamespaceFilter(v, "searge_id")
        }
        // remove static initializers, not needed in the documentation
        intercept(::StaticInitializerFilter)
        // remove overrides of java/lang/Object, they are implicit
        intercept(::ObjectOverrideFilter)
        // remove obfuscated method parameter names, they are a filler from Searge
        intercept(::MethodArgSourceFilter)

        contributors { versionWorkspace ->
            val mojangProvider = MojangManifestAttributeProvider(versionWorkspace, objectMapper)
            val spigotProvider = SpigotManifestProvider(versionWorkspace, objectMapper)

            val prependedClasses = mutableListOf<String>()

            listOf(
                VanillaMappingContributor(versionWorkspace, mojangProvider),
                MojangServerMappingResolver(versionWorkspace, mojangProvider),
                IntermediaryMappingResolver(versionWorkspace, sharedCache),
                YarnMappingResolver(versionWorkspace, yarnProvider),
                SeargeMappingResolver(versionWorkspace, sharedCache),
                WrappingContributor(SpigotClassMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                    // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
                    // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
                    LegacySpigotMappingPrepender(it, prependedClasses = prependedClasses, prependEverything = versionWorkspace.version.id == "1.16.5")
                },
                WrappingContributor(SpigotMemberMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                    LegacySpigotMappingPrepender(it, prependedClasses = prependedClasses)
                }
            )
        }

        if (noJoined) {
            joinedOutputPath { null }
        }
    }

    val mappingProvider = ResolvingMappingProvider(mappingConfig, objectMapper, xmlMapper)
    val analyzer = MappingAnalyzerImpl(
        AnalysisOptions(
            innerClassNameCompletionCandidates = setOf("spigot"),
            inheritanceAdditionalNamespaces = setOf("searge") // mojang could be here too for maximal parity, but that's in exchange for a little bit of performance
        )
    )

    val webConfig = buildWebConfig {
        welcomeMessage(
            """
                <h1>Welcome to the browser for Minecraft: Java Edition server-side mappings!</h1>
                <br/>
                <p>
                    You can move through this site by following links to specific versions/packages/classes/...
                    or use the nifty search field in the top right corner (appears when in a versioned page!).
                </p>
                <br/>
                <p>
                    It is possible that there are errors in mappings displayed here, but we've tried to make them as close as possible to the runtime naming.<br/>
                    If you run into such an error, please report it at <a href="https://github.com/zlataovce/takenaka/issues/new">the issue tracker</a>!
                </p>
            """.trimIndent()
        )
        if (!synthetic) {
            welcomeMessage("$welcomeMessage<br/><strong>NOTE: This build of the site excludes synthetic members (generated by the compiler, i.e. not in the source code).</strong>")
        }

        emitMetaTags(!noMeta)

        logger.info { "using minification mode $minifier" }
        if (minifier != MinifierImpls.NONE) {
            transformer(Minifier(isDeterministic = minifier == MinifierImpls.DETERMINISTIC))
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

        index(indexers)

        replaceCraftBukkitVersions("spigot")
        friendlyNamespaces("mojang", "spigot", "yarn", "searge", "intermediary", "source")
        namespace("mojang", "Mojang", "#4D7C0F", MojangServerMappingResolver.META_LICENSE)
        namespace("spigot", "Spigot", "#CA8A04", AbstractSpigotMappingResolver.META_LICENSE)
        namespace("yarn", "Yarn", "#626262", YarnMappingResolver.META_LICENSE)
        namespace("searge", "Searge", "#B91C1C", SeargeMappingResolver.META_LICENSE)
        namespace("intermediary", "Intermediary", "#0369A1", IntermediaryMappingResolver.META_LICENSE)
        namespace("source", "Obfuscated", "#581C87")
    }

    val generator = WebGenerator(workspace, webConfig)

    logger.info { "starting generator" }
    val time = measureTimeMillis {
        runBlocking {
            val mappings = mappingProvider.get(analyzer)
            analyzer.problemKinds.forEach { kind ->
                if (synthetic && kind == StandardProblemKinds.SYNTHETIC) return@forEach

                analyzer.acceptResolutions(kind)
            }

            generator.generate(mappings)
        }
    }
    logger.info { "generator finished in ${time / 1000} second(s)" }
}
