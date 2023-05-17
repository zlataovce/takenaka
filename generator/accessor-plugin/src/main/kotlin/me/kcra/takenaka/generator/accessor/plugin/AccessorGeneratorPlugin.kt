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
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.plugin.tasks.GenerateAccessorsTask
import me.kcra.takenaka.generator.accessor.plugin.tasks.ResolveMappingsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

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
        val config = target.extensions.create("accessors", AccessorGeneratorExtension::class.java, target)

        // automatically adds two dependent tasks for basic Mojang-based server accessor generation
        val options = config.strictCache.map { isStrict ->
            buildWorkspaceOptions {
                if (!isStrict) {
                    relaxedCache()
                }
            }
        }
        val mappingTask = target.tasks.create("resolveMappings", ResolveMappingsTask::class.java) { task ->
            task.group = "takenaka"
            task.description = "Resolves a basic set of mappings for development on Mojang-based servers."

            task.cacheDir.set(config.cacheDirectory)
            task.versions.set(config.versions)
            task.options.set(options)
        }
        target.tasks.create("generateAccessors", GenerateAccessorsTask::class.java) { task ->
            task.group = "takenaka"
            task.description = "Generates reflective accessors."
            task.dependsOn("resolveMappings")

            task.outputDir.set(config.outputDirectory)
            task.mappings.set(mappingTask.mappings)
            task.accessors.set(config.accessors)
            task.basePackage.set(config.basePackage)
            task.languageFlavor.set(config.languageFlavor)
            task.accessedNamespaces.set(config.accessedNamespaces)
            task.options.set(options)
        }
    }
}
