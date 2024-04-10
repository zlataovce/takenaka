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

package me.kcra.takenaka.generator.accessor.plugin

import me.kcra.takenaka.core.cachedVersionManifest
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.plugin.tasks.GenerateAccessorsTask
import me.kcra.takenaka.generator.accessor.plugin.tasks.ResolveMappingsTask
import me.kcra.takenaka.generator.accessor.plugin.tasks.TraceAccessorsTask
import me.kcra.takenaka.generator.common.provider.impl.SimpleMappingProvider
import me.kcra.takenaka.gradle.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*

/**
 * A Gradle plugin interface for [AccessorGenerator].
 *
 * *If you wish to skip the automatic task configuration and make the tasks yourself, don't apply the plugin.*
 *
 * @author Matouš Kučera
 */
class AccessorGeneratorPlugin : Plugin<Project> {
    /**
     * Apply this plugin to the given target object.
     *
     * @param target the target object
     */
    override fun apply(target: Project) {
        val manifestCacheFile = target.layout.buildDirectory.file(System.getProperty("me.kcra.takenaka.manifest.file", "takenaka/cache/manifest.json"))

        val manifest = objectMapper().cachedVersionManifest(manifestCacheFile.get().asFile.toPath()) // please switch to NIO paths, gradle!!
        val config = target.extensions.create<AccessorGeneratorExtension>("accessors", target, manifest)

        // automatically adds tasks for basic Mojang-based accessor generation
        val mappingBundle by target.configurations.registering
        val resolveMappings by target.tasks.registering(ResolveMappingsTask::class) {
            group = "takenaka"
            description = "Resolves a basic set of mappings for development on Mojang-based software."

            this.cacheDir.set(config.cacheDirectory)
            this.versions.set(config.versions)
            this.namespaces.set(project.provider { config.namespaces.get() + config.historyNamespaces.get() })
            this.relaxedCache.set(config.relaxedCache)
            this.manifest.set(manifest)
            this.mappingBundle.fileProvider(mappingBundle.flatMap { mb ->
                project.provider { // Kotlin doesn't like returning null from Provider#map
                    if (!mb.isEmpty) {
                        return@provider requireNotNull(mb.singleOrNull()) {
                            "mappingBundle configuration may only have a single file"
                        }
                    }

                    return@provider null
                }
            })
        }
        val generateAccessors by target.tasks.registering(GenerateAccessorsTask::class) {
            group = "takenaka"
            description = "Generates reflective accessors."
            dependsOn(resolveMappings)

            this.outputDir.set(config.outputDirectory)
            this.mappingProvider.set(resolveMappings.flatMap { rm -> rm.mappings.map(::SimpleMappingProvider) })
            this.accessors.set(config.accessors)
            this.codeLanguage.set(config.codeLanguage)
            this.accessorType.set(config.accessorType)
            this.namespaces.set(config.namespaces)
            this.historyNamespaces.set(config.historyNamespaces)
            this.historyIndexNamespace.set(config.historyIndexNamespace)
            this.namingStrategy.set(config.namingStrategy)
            this.runtimePackage.set(config.runtimePackage)
        }
        val traceAccessors by target.tasks.creating(TraceAccessorsTask::class) {
            group = "takenaka"
            description = "Creates an accessor generation report."
            dependsOn(resolveMappings)

            this.outputDir.set(config.outputDirectory)
            this.mappingProvider.set(resolveMappings.flatMap { rm -> rm.mappings.map(::SimpleMappingProvider) })
            this.accessors.set(config.accessors)
            this.codeLanguage.set(config.codeLanguage)
            this.accessorType.set(config.accessorType)
            this.namespaces.set(config.namespaces)
            this.historyNamespaces.set(config.historyNamespaces)
            this.historyIndexNamespace.set(config.historyIndexNamespace)
            this.namingStrategy.set(config.namingStrategy)
            this.runtimePackage.set(config.runtimePackage)
        }

        target.tasks.withType<JavaCompile> {
            dependsOn(generateAccessors)
        }

        @Suppress("UNCHECKED_CAST")
        runCatching { Class.forName("org.jetbrains.kotlin.gradle.tasks.KotlinCompile") as Class<Task> }
            .onSuccess { klass ->
                target.tasks.withType(klass) {
                    dependsOn(generateAccessors)
                }
            }

        target.afterEvaluate {
            val defaultLocation = layout.buildDirectory.dir("takenaka/output").get().asFile

            // add the directory to the main source set, if it was set by the convention
            if (config.outputDirectory.get().asFile == defaultLocation) {
                extensions.getByType<JavaPluginExtension>().sourceSets["main"].java.srcDir(defaultLocation)
            }
        }
    }
}

/**
 * Builds the dependency notation for the takenaka `generator-accessor-runtime` module at the version of this Gradle plugin.
 *
 * @param group the Maven dependency group, defaults to the group of the build of this Gradle plugin
 * @param version the dependency version, defaults to the version of this Gradle plugin
 * @return the dependency
 */
@Suppress("UnusedReceiverParameter") // scope restriction
fun DependencyHandler.accessorRuntime(group: String = BuildConfig.BUILD_MAVEN_GROUP, version: String = BuildConfig.BUILD_VERSION): Any =
    "$group:generator-accessor-runtime:$version"
