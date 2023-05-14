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
        val config = target.extensions.create("accessors", AccessorGeneratorExtension::class.java)

        target.tasks.create("generateAccessors", GenerateAccessorsTask::class.java) { task ->
            task.outputDir.set(config.outputDirectory)
            task.cacheDir.set(config.cacheDirectory)
            task.versions.set(config.versions)
            task.accessors.set(config.accessors)
            task.basePackage.set(config.basePackage)
            task.languageFlavor.set(config.languageFlavor)
            task.options.set(
                config.strictCache.map { isStrict ->
                    buildWorkspaceOptions {
                        if (!isStrict) {
                            relaxedCache()
                        }
                    }
                }
            )
        }
    }
}
