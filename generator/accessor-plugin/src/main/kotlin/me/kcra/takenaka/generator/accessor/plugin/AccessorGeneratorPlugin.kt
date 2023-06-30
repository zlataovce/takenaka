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

package me.kcra.takenaka.generator.accessor.plugin

import me.kcra.takenaka.core.buildWorkspaceOptions
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.versionManifest
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.plugin.tasks.GenerateAccessorsTask
import me.kcra.takenaka.generator.accessor.plugin.tasks.ResolveMappingsTask
import me.kcra.takenaka.gradle.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*

/**
 * A Gradle plugin interface for [AccessorGenerator].
 *
 * @author Matouš Kučera
 */
class AccessorGeneratorPlugin : Plugin<Project> {
    /**
     * Apply this plugin to the given target object.
     *
     * @param target The target object
     */
    override fun apply(target: Project) {
        val manifest = objectMapper().versionManifest()
        val config = target.extensions.create<AccessorGeneratorExtension>("accessors", target, manifest)

        // automatically adds tasks for basic Mojang-based server accessor generation
        val options = config.strictCache.map { isStrict ->
            buildWorkspaceOptions {
                if (!isStrict) {
                    relaxedCache()
                }
            }
        }
        val mappingBundle by target.configurations.creating
        val resolveMappings by target.tasks.creating(ResolveMappingsTask::class) {
            group = "takenaka"
            description = "Resolves a basic set of mappings for development on Mojang-based servers."

            this.cacheDir.set(config.cacheDirectory)
            this.versions.set(config.versions)
            this.options.set(options)
            this.manifest.set(manifest)
        }
        val generateAccessors by target.tasks.creating(GenerateAccessorsTask::class) {
            group = "takenaka"
            description = "Generates reflective accessors."
            dependsOn(resolveMappings)

            this.outputDir.set(config.outputDirectory)
            this.mappings.set(resolveMappings.mappings)
            this.accessors.set(config.accessors)
            this.basePackage.set(config.basePackage)
            this.languageFlavor.set(config.languageFlavor)
            this.accessorFlavor.set(config.accessorFlavor)
            this.accessedNamespaces.set(config.accessedNamespaces)
            this.historyNamespaces.set(config.historyNamespaces)
            this.historyIndexNamespace.set(config.historyIndexNamespace)
            this.options.set(options)
        }

        target.tasks.withType<JavaCompile> {
            dependsOn(generateAccessors)
        }

        target.afterEvaluate {
            val defaultLocation = layout.buildDirectory.dir("takenaka/output").get().asFile

            // add the directory to the main source set, if it was set by the convention
            if (config.outputDirectory.get().asFile == defaultLocation) {
                extensions.getByType<JavaPluginExtension>().sourceSets["main"].java.srcDir(defaultLocation)
            }

            // set the task bundle file, if the mappingBundle configuration is not empty
            if (!mappingBundle.isEmpty) {
                resolveMappings.mappingBundle.set(mappingBundle.singleFile)
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
fun DependencyHandler.accessorRuntime(group: String = BuildConfig.BUILD_MAVEN_GROUP, version: String = BuildConfig.BUILD_VERSION): Any =
    "$group:generator-accessor-runtime:$version"
