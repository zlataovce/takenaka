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
import me.kcra.takenaka.core.DefaultWorkspaceOptions
import me.kcra.takenaka.core.WorkspaceOptions
import me.kcra.takenaka.core.workspace
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorFlavor
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

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
     * The input mappings, probably linked from a [ResolveMappingsTask].
     */
    @get:Internal
    abstract val mappings: MutableMappingsMapProperty

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
     * The language of the generated code, defaults to [LanguageFlavor.JAVA].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.languageFlavor
     */
    @get:Input
    abstract val languageFlavor: Property<LanguageFlavor>

    /**
     * The form of the generated accessors, defaults to [AccessorFlavor.NONE].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.accessorFlavor
     */
    @get:Input
    abstract val accessorFlavor: Property<AccessorFlavor>

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
     * The workspace options, defaults to [DefaultWorkspaceOptions.RELAXED_CACHE].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.strictCache
     */
    @get:Input
    abstract val options: Property<WorkspaceOptions>

    /**
     * The output workspace ([outputDir]).
     */
    @get:Internal
    val outputWorkspace by lazy {
        workspace {
            rootDirectory(outputDir.asFile.get())
            options(this@GenerateAccessorsTask.options.get())
        }
    }

    init {
        outputDir.convention(project.layout.buildDirectory.dir("takenaka/output"))
        namespaceFriendlinessIndex.convention(listOf("mojang", "spigot", "yarn", "searge", "intermediary", "source"))
        languageFlavor.convention(LanguageFlavor.JAVA)
        accessorFlavor.convention(AccessorFlavor.NONE)
        craftBukkitVersionReplaceCandidates.convention(listOf("spigot"))
        options.convention(DefaultWorkspaceOptions.RELAXED_CACHE)
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
                languageFlavor = languageFlavor.get(),
                accessorFlavor = accessorFlavor.get(),
                namespaceFriendlinessIndex = namespaceFriendlinessIndex.get(),
                accessedNamespaces = accessedNamespaces.get(),
                craftBukkitVersionReplaceCandidates = craftBukkitVersionReplaceCandidates.get()
            )
        )

        outputWorkspace.clean()
        runBlocking {
            generator.generate(mappings.get())
        }
    }
}
