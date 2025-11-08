/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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

import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.compositeWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.analysis.impl.AnalysisOptions
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.analysis.impl.StandardProblemKinds
import me.kcra.takenaka.core.mapping.resolve.impl.*
import me.kcra.takenaka.core.util.md5Digest
import me.kcra.takenaka.core.util.updateAndHex
import me.kcra.takenaka.core.workspace
import me.kcra.takenaka.generator.common.provider.impl.ResolvingMappingProvider
import me.kcra.takenaka.generator.common.provider.impl.SimpleAncestryProvider
import me.kcra.takenaka.generator.common.provider.impl.SimpleMappingProvider
import me.kcra.takenaka.generator.common.provider.impl.buildMappingConfig
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.transformers.CSSInliningTransformer
import me.kcra.takenaka.generator.web.transformers.MinifyingTransformer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * Available namespaces.
 */
val NAMESPACES = mapOf(
    "mojang" to NamespaceDescription("Mojang", "#4D7C0F", AbstractMojangMappingResolver.META_LICENSE),
    "spigot" to NamespaceDescription("Spigot", "#CA8A04", AbstractSpigotMappingResolver.META_LICENSE),
    "yarn" to NamespaceDescription("Yarn", "#626262", YarnMappingResolver.META_LICENSE),
    "quilt" to NamespaceDescription("Quilt", "#9722ff", QuiltMappingResolver.META_LICENSE),
    "searge" to NamespaceDescription("Searge", "#B91C1C", SeargeMappingResolver.META_LICENSE),
    "intermediary" to NamespaceDescription("Intermediary", "#0369A1", IntermediaryMappingResolver.META_LICENSE),
    "hashed" to NamespaceDescription("Hashed", "#3344ff", null),
)

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
    val namespace by parser.option(ArgType.String, shortName = "n", description = "Target namespace, can be specified multiple times, order matters").multiple()
    val ancestryNamespace by parser.option(ArgType.String, shortName = "a", description = "Target ancestry namespace, can be specified multiple times, has to be a subset of --namespace").multiple()
    val cache by parser.option(ArgType.String, shortName = "c", description = "Caching directory for mappings and other resources").default("cache")
    val server by parser.option(ArgType.Boolean, description = "Include server mappings in the documentation").default(false)
    val client by parser.option(ArgType.Boolean, description = "Include client mappings in the documentation").default(false)
    val strictCache by parser.option(ArgType.Boolean, description = "Enforces strict cache validation").default(false)
    val clean by parser.option(ArgType.Boolean, description = "Removes previous build output and cache before launching").default(false)
    val noJoined by parser.option(ArgType.Boolean, description = "Don't cache joined mapping files").default(false)

    // generator-specific settings below

    val minifier by parser.option(ArgType.Choice<MinifierImpls>(), shortName = "m", description = "The minifier implementation used for minifying the documentation").default(MinifierImpls.NORMAL)
    val javadoc by parser.option(ArgType.String, shortName = "j", description = "Javadoc site that should be referenced in the documentation, can be specified multiple times").multiple()
    val synthetic by parser.option(ArgType.Boolean, shortName = "s", description = "Include synthetic classes and class members in the documentation").default(false)
    val noMeta by parser.option(ArgType.Boolean, description = "Don't emit HTML metadata tags in OpenGraph format").default(false)
    val noPseudoElems by parser.option(ArgType.Boolean, description = "Don't emit pseudo-elements (increases file size)").default(false)

    parser.parse(args)

    if (!server && !client) {
        logger.error { "no mappings were specified, add --server and/or --client" }
        exitProcess(-1)
    }

    val namespaceKeys = namespace.distinct().ifEmpty { NAMESPACES.keys }
    fun <T : MappingContributor> MutableCollection<T>.addIfSupported(contributor: T) {
        if (contributor.targetNamespace in namespaceKeys) add(contributor)
    }

    val resolvedNamespaces = namespaceKeys.map { nsKey ->
        val ns = NAMESPACES[nsKey]
        if (ns == null) {
            logger.error { "namespace $nsKey not found, has to be one of [${NAMESPACES.keys.joinToString()}]" }
            exitProcess(-1)
        }

        nsKey to ns
    }

    val ancestryNamespaces = namespaceKeys
        .filter(ancestryNamespace::contains)
        .ifEmpty {
            listOf("mojang", "spigot", "searge", "intermediary")
                .filter(namespaceKeys::contains)
        }

    val workspace = workspace {
        rootDirectory(output)
    }
    val cacheWorkspace = compositeWorkspace {
        rootDirectory(cache)
    }

    if (clean) {
        workspace.clean()
        cacheWorkspace.clean()
    }

    // generator setup below

    val mappingsCache = cacheWorkspace.createCompositeWorkspace {
        name = "mappings"
    }
    val sharedCache = cacheWorkspace.createWorkspace {
        name = "shared"
    }

    val yarnProvider = YarnMetadataProvider(sharedCache, relaxedCache = !strictCache)
    val quiltProvider = QuiltMetadataProvider(sharedCache, relaxedCache = !strictCache)
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
//        intercept(::MethodArgSourceFilter) // TODO: fix the interceptor
        // intern names to save memory
        intercept(::StringPoolingAdapter)

        contributors { versionWorkspace ->
            val mojangProvider = MojangManifestAttributeProvider(versionWorkspace, relaxedCache = !strictCache)
            val spigotProvider = SpigotManifestProvider(versionWorkspace, relaxedCache = !strictCache)

            buildList {
                if (server) {
                    add(VanillaServerMappingContributor(versionWorkspace, mojangProvider, relaxedCache = !strictCache))
                    addIfSupported(MojangServerMappingResolver(versionWorkspace, mojangProvider))
                }
                if (client) {
                    add(VanillaClientMappingContributor(versionWorkspace, mojangProvider, relaxedCache = !strictCache))
                    addIfSupported(MojangClientMappingResolver(versionWorkspace, mojangProvider))
                }

                addIfSupported(IntermediaryMappingResolver(versionWorkspace, sharedCache))
                addIfSupported(HashedMappingResolver(versionWorkspace))
                addIfSupported(YarnMappingResolver(versionWorkspace, yarnProvider, relaxedCache = !strictCache))
                addIfSupported(QuiltMappingResolver(versionWorkspace, quiltProvider, relaxedCache = !strictCache))
                addIfSupported(SeargeMappingResolver(versionWorkspace, sharedCache, relaxedCache = !strictCache))

                // Spigot resolvers have to be last
                if (server) {
                    val link = LegacySpigotMappingPrepender.Link()

                    addIfSupported(
                        link.createPrependingContributor(
                            SpigotClassMappingResolver(
                                versionWorkspace,
                                spigotProvider,
                                relaxedCache = !strictCache
                            ),
                            // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
                            // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
                            prependEverything = versionWorkspace.version.id == "1.16.5"
                        )
                    )
                    addIfSupported(
                        link.createPrependingContributor(
                            SpigotMemberMappingResolver(
                                versionWorkspace,
                                spigotProvider,
                                relaxedCache = !strictCache
                            )
                        )
                    )
                }
            }
        }

        joinedOutputPath { workspace ->
            if (noJoined) return@joinedOutputPath null
            val platform = when {
                client && server -> "client+server"
                client -> "client"
                else -> "server"
            }

            workspace["${md5Digest.updateAndHex(namespaceKeys.sorted().joinToString(","))}.$platform.tiny"]
        }
    }

    val mappingProvider = ResolvingMappingProvider(mappingConfig)
    val analyzer = MappingAnalyzerImpl(
        // if the namespaces are missing, nothing happens anyway - no need to configure based on resolvedNamespaces
        AnalysisOptions(
            innerClassNameCompletionCandidates = setOf("spigot"),
            inheritanceAdditionalNamespaces = setOf("searge") // mojang could be here too for maximal parity, but that's in exchange for a little bit of performance
        )
    )

    val webConfig = buildWebConfig {
        val chosenMappings = when {
            client && server -> "client- and server-side"
            client -> "client-side"
            else -> "server-side"
        }

        welcomeMessage(
            """
                <h1>Welcome to the browser for Minecraft: Java Edition $chosenMappings mappings!</h1>
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
        emitPseudoElements(!noPseudoElems)

        transformer(CSSInliningTransformer("cdn.jsdelivr.net"))
        logger.info { "using minification mode $minifier" }
        if (minifier != MinifierImpls.NONE) {
            transformer(MinifyingTransformer(isDeterministic = minifier == MinifierImpls.DETERMINISTIC))
        }

        val indexers = mutableListOf<ClassSearchIndex>(modularClassSearchIndexOf(JDK_21_BASE_URL))
        javadoc.mapTo(indexers) { javadocDef ->
            val javadocParams = javadocDef.split('+', limit = 2)

            when (javadocParams.size) {
                2 -> classSearchIndexOf(javadocParams[1], javadocParams[0])
                else -> modularClassSearchIndexOf(javadocParams[0])
            }
        }

        index(indexers)
        logger.info { "using ${indexers.size} javadoc indexer(s)" }

        namespaces += resolvedNamespaces
        friendlyNamespaces(resolvedNamespaces.map { (name, _) -> name })

        // replace CraftBukkit version strings if a Spigot namespace is requested
        if ("spigot" in namespaces) replaceCraftBukkitVersions("spigot")

        // source/Obfuscated is always present
        friendlyNamespaces("source")
        namespace("source", "Obfuscated", "#581C87")

        logger.info { "using [${namespaceFriendlinessIndex.joinToString()}] namespaces" }
        logger.info { "using [${ancestryNamespaces.joinToString()}] ancestry namespaces" }
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

            generator.generate(
                SimpleMappingProvider(mappings),
                SimpleAncestryProvider(null, ancestryNamespaces)
            )
        }
    }
    logger.info { "generator finished in ${time / 1000} second(s)" }
}
