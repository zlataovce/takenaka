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
import me.kcra.takenaka.core.*
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.resolve.impl.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.common.BundledMappingProvider
import me.kcra.takenaka.generator.common.ResolvingMappingProvider
import me.kcra.takenaka.generator.common.buildMappingConfig
import net.fabricmc.mappingio.tree.MappingTree
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

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
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.versions
     */
    @get:Input
    abstract val versions: ListProperty<String>

    /**
     * The workspace options, defaults to [DefaultWorkspaceOptions.RELAXED_CACHE].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.strictCache
     */
    @get:Input
    abstract val options: Property<WorkspaceOptions>

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
            options(this@ResolveMappingsTask.options.get())
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
        options.convention(DefaultWorkspaceOptions.RELAXED_CACHE)
    }

    /**
     * Runs the task.
     */
    @TaskAction
    fun run() {
        val objectMapper = objectMapper()
        val manifest = objectMapper.versionManifest()

        // resolve mappings on this system, if a bundle is not available
        val mappingProvider = if (mappingBundle.isPresent) {
            BundledMappingProvider(mappingBundle.get().asFile.toPath(), manifest)
        } else {
            val xmlMapper = XmlMapper()

            val yarnProvider = YarnMetadataProvider(sharedCacheWorkspace, xmlMapper)
            val mappingConfig = buildMappingConfig {
                version(this@ResolveMappingsTask.versions.get())
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

                contributors { versionWorkspace ->
                    val mojangProvider = MojangManifestAttributeProvider(versionWorkspace, objectMapper)
                    val spigotProvider = SpigotManifestProvider(versionWorkspace, objectMapper)

                    val prependedClasses = mutableListOf<String>()

                    listOf(
                        VanillaMappingContributor(versionWorkspace, mojangProvider),
                        MojangServerMappingResolver(versionWorkspace, mojangProvider),
                        IntermediaryMappingResolver(versionWorkspace, sharedCacheWorkspace),
                        YarnMappingResolver(versionWorkspace, yarnProvider),
                        SeargeMappingResolver(versionWorkspace, sharedCacheWorkspace),
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
            }

            ResolvingMappingProvider(mappingConfig, manifest, xmlMapper)
        }

        val analyzer = MappingAnalyzerImpl(
            MappingAnalyzerImpl.AnalysisOptions(
                innerClassNameCompletionCandidates = setOf("spigot")
            )
        )

        runBlocking {
            val mappingMap = mappingProvider.get(analyzer)
            analyzer.acceptResolutions()

            mappings.set(mappingMap)
        }
    }
}
