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
import me.kcra.takenaka.core.workspace
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorType
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.CodeLanguage
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.common.provider.MappingProvider
import me.kcra.takenaka.generator.common.provider.impl.SimpleAncestryProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * The default history index namespace.
 */
const val DEFAULT_INDEX_NS = "takenaka_node"

/**
 * A Gradle task that generates accessors from mappings.
 *
 * @author Matouš Kučera
 */
abstract class GenerateAccessorsTask : DefaultTask() {
    /**
     * The output directory, defaults to `build/takenaka/output`.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.outputDirectory
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /**
     * The input mapping provider, probably linked from a [ResolveMappingsTask].
     */
    @get:Internal
    abstract val mappingProvider: Property<MappingProvider>

    /**
     * Class accessor models.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.accessors
     */
    @get:Input
    abstract val accessors: ListProperty<ClassAccessor>

    /**
     * Base package of the generated accessors, required.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.basePackage
     */
    @get:Input
    abstract val basePackage: Property<String>

    /**
     * An ordered list of namespaces that will be considered when selecting a "friendly" name,
     * defaults to "mojang", "spigot", "yarn", "searge", "intermediary" and "source".
     */
    @get:Input
    abstract val namespaceFriendlinessIndex: ListProperty<String>

    /**
     * The language of the generated code, defaults to [CodeLanguage.JAVA].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.languageFlavor
     */
    @get:Input
    abstract val codeLanguage: Property<CodeLanguage>

    /**
     * The form of the generated accessors, defaults to [AccessorType.NONE].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.accessorFlavor
     */
    @get:Input
    abstract val accessorType: Property<AccessorType>

    /**
     * Namespaces that should be used in accessors, empty if all namespaces should be used.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.accessedNamespaces
     */
    @get:Input
    abstract val accessedNamespaces: ListProperty<String>

    /**
     * Namespaces that should have [me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion] applied
     * (most likely Spigot mappings or a flavor of them), defaults to "spigot".
     */
    @get:Input
    abstract val craftBukkitVersionReplaceCandidates: ListProperty<String>

    /**
     * Namespaces that should be used for computing history, defaults to "mojang", "spigot", "searge" and "intermediary".
     */
    @get:Input
    abstract val historyNamespaces: ListProperty<String>

    /**
     * Namespace that contains ancestry node indices, null if ancestry should be recomputed from scratch, defaults to [DEFAULT_INDEX_NS].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.historyIndexNamespace
     */
    @get:Input
    abstract val historyIndexNamespace: Property<String?>

    /**
     * The output workspace ([outputDir]).
     */
    @get:Internal
    val outputWorkspace by lazy {
        workspace {
            rootDirectory(outputDir.asFile.get())
        }
    }

    init {
        outputDir.convention(project.layout.buildDirectory.dir("takenaka/output"))
        namespaceFriendlinessIndex.convention(listOf("mojang", "spigot", "yarn", "searge", "intermediary", "source"))
        codeLanguage.convention(CodeLanguage.JAVA)
        accessorType.convention(AccessorType.NONE)
        craftBukkitVersionReplaceCandidates.convention(listOf("spigot"))
        historyNamespaces.convention(listOf("mojang", "spigot", "searge", "intermediary"))
        historyIndexNamespace.convention(DEFAULT_INDEX_NS)
    }

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
