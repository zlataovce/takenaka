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

package me.kcra.takenaka.generator.accessor.plugin.tasks

import me.kcra.takenaka.core.workspace
import me.kcra.takenaka.generator.accessor.DEFAULT_RUNTIME_PACKAGE
import me.kcra.takenaka.generator.accessor.AccessorType
import me.kcra.takenaka.generator.accessor.CodeLanguage
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.naming.NamingStrategy
import me.kcra.takenaka.generator.accessor.naming.StandardNamingStrategies
import me.kcra.takenaka.generator.accessor.naming.prefixed
import me.kcra.takenaka.generator.accessor.naming.resolveSimpleConflicts
import me.kcra.takenaka.generator.common.provider.MappingProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory

/**
 * The default history index namespace.
 */
const val DEFAULT_INDEX_NS = "takenaka_node"

/**
 * An [me.kcra.takenaka.generator.accessor.AccessorGenerator] runner task base.
 *
 * @author Matouš Kučera
 */
abstract class GenerationTask : DefaultTask() {
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
     * Base package of the generated accessors.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.basePackage
     */
    @Deprecated("The base package concept was superseded by naming strategies.")
    @get:Optional
    @get:Input
    abstract val basePackage: Property<String>

    /**
     * An ordered list of namespaces that will be considered when selecting a "friendly" name,
     * defaults to "mojang", "spigot", "yarn", "quilt", "searge", "intermediary", "hashed" and "source".
     */
    @get:Input
    abstract val namespaceFriendlinessIndex: ListProperty<String>

    /**
     * The language of the generated code, defaults to [CodeLanguage.JAVA].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.codeLanguage
     */
    @get:Input
    abstract val codeLanguage: Property<CodeLanguage>

    /**
     * The form of the generated accessors, defaults to [AccessorType.NONE].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.accessorType
     */
    @get:Input
    abstract val accessorType: Property<AccessorType>

    /**
     * Namespaces that should be used in accessors, defaults to all supported namespaces ("mojang", "spigot", "yarn", "quilt", "searge", "intermediary" and "hashed").
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.namespaces
     */
    @get:Input
    abstract val namespaces: ListProperty<String>

    /**
     * Alias for [namespaces].
     */
    @get:Internal
    @Deprecated("Use namespaces.", ReplaceWith("namespaces"))
    val accessedNamespaces: ListProperty<String>
        get() = namespaces

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
     * Strategy used to name generated classes and their members.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.namingStrategy
     */
    @get:Input
    abstract val namingStrategy: Property<NamingStrategy>

    /**
     * Package containing the accessor runtime, defaults to [DEFAULT_RUNTIME_PACKAGE].
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.runtimePackage
     */
    @get:Input
    abstract val runtimePackage: Property<String>

    /**
     * Base URL of the mapping website including protocol, defaults to `null`.
     *
     * @see me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorExtension.mappingWebsite
     */
    @get:Input
    abstract val mappingWebsite: Property<String>

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
        namespaceFriendlinessIndex.convention(listOf("mojang", "spigot", "yarn", "quilt", "searge", "intermediary", "hashed", "source"))
        codeLanguage.convention(CodeLanguage.JAVA)
        accessorType.convention(AccessorType.NONE)
        craftBukkitVersionReplaceCandidates.convention(listOf("spigot"))
        namespaces.convention(listOf("mojang", "spigot", "yarn", "quilt", "searge", "intermediary", "hashed"))
        historyNamespaces.convention(listOf("mojang", "spigot", "searge", "intermediary"))
        historyIndexNamespace.convention(DEFAULT_INDEX_NS)
        @Suppress("DEPRECATION")
        namingStrategy.convention(basePackage.map { pack -> StandardNamingStrategies.SIMPLE.prefixed(pack).resolveSimpleConflicts() })
        runtimePackage.convention(DEFAULT_RUNTIME_PACKAGE)
        mappingWebsite.convention(null)
    }
}
