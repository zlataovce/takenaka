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

import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.common.provider.impl.SimpleAncestryProvider
import org.gradle.api.tasks.TaskAction

/**
 * A Gradle task that generates accessors from mappings.
 *
 * @author Matouš Kučera
 */
abstract class GenerateAccessorsTask : GenerationTask() {
    /**
     * Runs the task.
     */
    @TaskAction
    fun run() {
        val generator = AccessorGenerator(
            outputWorkspace,
            AccessorConfiguration(
                accessors = accessors.get(),
                basePackage = basePackage.get(),
                codeLanguage = codeLanguage.get(),
                accessorType = accessorType.get(),
                namespaceFriendlinessIndex = namespaceFriendlinessIndex.get(),
                accessedNamespaces = accessedNamespaces.get(),
                craftBukkitVersionReplaceCandidates = craftBukkitVersionReplaceCandidates.get()
            )
        )

        outputWorkspace.clean()
        runBlocking {
            generator.generate(
                mappingProvider.get(),
                SimpleAncestryProvider(historyIndexNamespace.get(), historyNamespaces.get())
            )
        }
    }
}
