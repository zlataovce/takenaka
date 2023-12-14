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

package me.kcra.takenaka.generator.accessor.plugin.tasks

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionManifest
import me.kcra.takenaka.core.compositeWorkspace
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.analysis.impl.AnalysisOptions
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.resolve.impl.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.accessor.plugin.PlatformTristate
import me.kcra.takenaka.generator.common.provider.impl.BundledMappingProvider
import me.kcra.takenaka.generator.common.provider.impl.ResolvingMappingProvider
import me.kcra.takenaka.generator.common.provider.impl.buildMappingConfig
import net.fabricmc.mappingio.tree.MappingTree
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import java.util.zip.ZipFile

/**
 * A [me.kcra.takenaka.core.mapping.MutableMappingsMap], but as a Gradle [MapProperty].
 */
typealias MutableMappingsMapProperty = MapProperty<Version, MappingTree>

/**
 * A Gradle task that resolves and analyzes mappings.
 *
 * Mappings can be resolved from a mapping bundle, defined using the [mappingBundle] property, or
 * fetched and saved directly, defined using the [versions] property (only basic mappings for Mojang-based servers).
 *
 * **Mapping bundle content is not analyzed!**
 *
 * @author Matouš Kučera
 */
abstract class ResolveMappingsTask : DefaultTask() {
    /**
     * The cache directory, defaults to `build/takenaka/cache`.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.cacheDirectory
     */
    @get:OutputDirectory
    abstract val cacheDir: DirectoryProperty

    /**
     * A mapping bundle, optional, defined via the `mappingBundle` dependency configuration by default.
     */
    @get:Optional
    @get:InputFile
    abstract val mappingBundle: RegularFileProperty

    /**
     * Versions to be mapped.
     *
     * In case that a mapping bundle is selected ([mappingBundle] is present),
     * this property is used for selecting a version subset within the bundle
     * (every version from the bundle is mapped if no version is specified here).
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.versions
     * @see BundledMappingProvider.versions
     */
    @get:Input
    abstract val versions: SetProperty<String>

    /**
     * Whether output cache verification constraints should be relaxed, defaults to true.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.relaxedCache
     */
    @get:Input
    abstract val relaxedCache: Property<Boolean>

    /**
     * The mapped platform(s), defaults to [PlatformTristate.SERVER].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.platform
     */
    @get:Input
    abstract val platform: Property<PlatformTristate>

    /**
     * The version manifest.
     */
    @get:Input
    abstract val manifest: Property<VersionManifest>

    /**
     * The resolved mappings.
     */
    @get:Internal
    abstract val mappings: MutableMappingsMapProperty

    /**
     * The root cache workspace ([cacheDir]).
     */
    @get:Internal
    val cacheWorkspace by lazy {
        compositeWorkspace {
            rootDirectory(cacheDir.asFile.get())
        }
    }

    /**
     * The shared cache workspace, mainly for manifests and maven-metadata.xml files.
     */
    @get:Internal
    val sharedCacheWorkspace by lazy {
        cacheWorkspace.createWorkspace {
            name = "shared"
        }
    }

    /**
     * The mapping cache workspace.
     */
    @get:Internal
    val mappingCacheWorkspace by lazy {
        cacheWorkspace.createCompositeWorkspace {
            name = "mappings"
        }
    }

    init {
        cacheDir.convention(project.layout.buildDirectory.dir("takenaka/cache"))
        relaxedCache.convention(true)
        platform.convention(PlatformTristate.SERVER)

        // manual up-to-date checking, it's an Internal property
        outputs.upToDateWhen {
            val mappings = mappings.orNull?.keys?.map(Version::id) ?: emptyList()

            val requiredVersions = versions.orNull ?: emptySet<String>()
            val versions = if (mappingBundle.isPresent) {
                ZipFile(mappingBundle.get().asFile).use { zf ->
                    zf.entries()
                        .asSequence()
                        .mapNotNull { entry ->
                            if (entry.isDirectory || !entry.name.endsWith(".tiny")) {
                                return@mapNotNull null
                            }

                            entry.name.substringAfterLast('/').removeSuffix(".tiny")
                        }
                        .filterTo(mutableSetOf()) { version ->
                            requiredVersions.isEmpty() || version in requiredVersions
                        }
                }
            } else {
                requiredVersions
            }

            mappings.size == versions.size && versions.containsAll(mappings)
        }
    }

    /**
     * Runs the task.
     */
    @TaskAction
    fun run() {
        val objectMapper = objectMapper()

        val requiredPlatform = platform.get()
        val requiredVersions = versions.get().toList()
        val resolvedMappings = runBlocking {
            // resolve mappings on this system, if a bundle is not available
            if (mappingBundle.isPresent) {
                BundledMappingProvider(mappingBundle.get().asFile.toPath(), requiredVersions, manifest.get()).get()
            } else {
                val xmlMapper = XmlMapper()

                val yarnProvider = YarnMetadataProvider(sharedCacheWorkspace, xmlMapper, relaxedCache.get())
                val quiltProvider = QuiltMetadataProvider(sharedCacheWorkspace, xmlMapper, relaxedCache.get())
                val mappingConfig = buildMappingConfig {
                    version(requiredVersions)
                    workspace(mappingCacheWorkspace)

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
                    // intern names to save memory
                    intercept(::StringInterningAdapter)

                    contributors { versionWorkspace ->
                        val mojangProvider = MojangManifestAttributeProvider(versionWorkspace, objectMapper, relaxedCache.get())
                        val spigotProvider = SpigotManifestProvider(versionWorkspace, objectMapper, relaxedCache.get())

                        buildList {
                            if (requiredPlatform.wantsServer) {
                                add(VanillaServerMappingContributor(versionWorkspace, mojangProvider, relaxedCache.get()))
                                add(MojangServerMappingResolver(versionWorkspace, mojangProvider))
                            }
                            if (requiredPlatform.wantsClient) {
                                add(VanillaClientMappingContributor(versionWorkspace, mojangProvider, relaxedCache.get()))
                                add(MojangClientMappingResolver(versionWorkspace, mojangProvider))
                            }

                            add(IntermediaryMappingResolver(versionWorkspace, sharedCacheWorkspace))
                            add(YarnMappingResolver(versionWorkspace, yarnProvider, relaxedCache.get()))
                            add(QuiltMappingResolver(versionWorkspace, quiltProvider, relaxedCache.get()))
                            add(SeargeMappingResolver(versionWorkspace, sharedCacheWorkspace, relaxedCache = relaxedCache.get()))

                            // Spigot resolvers have to be last
                            if (requiredPlatform.wantsServer) {
                                val link = LegacySpigotMappingPrepender.Link()

                                add(
                                    // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
                                    // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
                                    link.createPrependingContributor(
                                        SpigotClassMappingResolver(versionWorkspace, xmlMapper, spigotProvider, relaxedCache.get()),
                                        prependEverything = versionWorkspace.version.id == "1.16.5"
                                    )
                                )
                                add(link.createPrependingContributor(SpigotMemberMappingResolver(versionWorkspace, xmlMapper, spigotProvider, relaxedCache.get())))
                            }
                        }
                    }

                    joinedOutputPath { workspace ->
                        val fileName = when (requiredPlatform) {
                            PlatformTristate.CLIENT_SERVER -> "client+server.tiny"
                            PlatformTristate.CLIENT -> "client.tiny"
                            else -> "server.tiny"
                        }

                        workspace[fileName]
                    }
                }

                val analyzer = MappingAnalyzerImpl(
                    AnalysisOptions(
                        innerClassNameCompletionCandidates = setOf("spigot"),
                        inheritanceAdditionalNamespaces = setOf("searge") // mojang could be here too for maximal parity, but that's in exchange for a little bit of performance
                    )
                )

                ResolvingMappingProvider(mappingConfig, manifest.get(), xmlMapper).get(analyzer)
                    .apply { analyzer.acceptResolutions() }
            }
        }

        mappings.set(resolvedMappings)
    }
}
