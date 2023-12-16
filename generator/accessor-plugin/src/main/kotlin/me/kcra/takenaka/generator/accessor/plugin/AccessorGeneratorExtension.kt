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

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionManifest
import me.kcra.takenaka.core.VersionRangeBuilder
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorType
import me.kcra.takenaka.generator.accessor.CodeLanguage
import me.kcra.takenaka.generator.accessor.model.*
import me.kcra.takenaka.generator.accessor.plugin.tasks.DEFAULT_INDEX_NS
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * A Gradle-specific builder for [AccessorConfiguration] with Minecraft presets.
 *
 * @property project the project
 * @property manifest the version manifest
 * @author Matouš Kučera
 */
abstract class AccessorGeneratorExtension(protected val project: Project, protected val manifest: VersionManifest) {
    /**
     * Versions to be mapped.
     *
     * In case that a mapping bundle is selected (the `mappingBundle` configuration has exactly one file),
     * this property is used for selecting a version subset within the bundle
     * (every version from the bundle is mapped if no version is specified here).
     *
     * @see me.kcra.takenaka.generator.common.provider.impl.BundledMappingProvider.versions
     */
    abstract val versions: SetProperty<String>

    /**
     * The output directory, defaults to `build/takenaka/output`.
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * The cache directory, defaults to `build/takenaka/cache`.
     */
    abstract val cacheDirectory: DirectoryProperty

    /**
     * Whether output cache verification constraints should be relaxed, defaults to true.
     */
    abstract val relaxedCache: Property<Boolean>

    /**
     * The mapped platform(s), defaults to [PlatformTristate.SERVER].
     */
    abstract val platform: Property<PlatformTristate>

    /**
     * Class accessor models.
     */
    abstract val accessors: ListProperty<ClassAccessor>

    /**
     * Base package of the generated accessors, required.
     */
    abstract val basePackage: Property<String>

    /**
     * The language of the generated code, defaults to [CodeLanguage.JAVA].
     */
    abstract val codeLanguage: Property<CodeLanguage>

    /**
     * The form of the generated accessors, defaults to [AccessorType.NONE].
     */
    abstract val accessorType: Property<AccessorType>

    /**
     * Namespaces that should be used in accessors, empty if all namespaces should be used.
     */
    abstract val accessedNamespaces: ListProperty<String>

    /**
     * Namespaces that should be used for computing history, defaults to "mojang", "spigot", "searge", "intermediary" and "hashed".
     */
    abstract val historyNamespaces: ListProperty<String>

    /**
     * Namespace that contains ancestry node indices, null if ancestry should be recomputed from scratch, defaults to [DEFAULT_INDEX_NS].
     */
    abstract val historyIndexNamespace: Property<String?>

    init {
        outputDirectory.convention(project.layout.buildDirectory.dir("takenaka/output"))
        cacheDirectory.convention(project.layout.buildDirectory.dir("takenaka/cache"))
        codeLanguage.convention(CodeLanguage.JAVA)
        accessorType.convention(AccessorType.NONE)
        historyNamespaces.convention(listOf("mojang", "spigot", "searge", "intermediary"))
        historyIndexNamespace.convention(DEFAULT_INDEX_NS)
        relaxedCache.convention(true)
        platform.convention(PlatformTristate.SERVER)
    }

    /**
     * Adds new versions to the [versions] property.
     *
     * @param versions the versions
     */
    fun versions(vararg versions: String) {
        this.versions.addAll(*versions)
    }

    /**
     * Adds new release versions to the [versions] property.
     *
     * @param older the older version range bound (inclusive)
     * @param newer the newer version range bound (inclusive)
     */
    fun versionRange(older: String, newer: String) {
        versionRange(older, newer) {
            includeTypes(Version.Type.RELEASE)
        }
    }

    /**
     * Adds new versions to the [versions] property.
     *
     * @param older the older version range bound (inclusive)
     * @param newer the newer version range bound (inclusive), defaults to the newest if null
     * @param block the version range configurator
     */
    @JvmOverloads
    fun versionRange(older: String, newer: String? = null, block: Action<VersionRangeBuilder>) {
        this.versions.addAll(VersionRangeBuilder(manifest, older, newer).apply(block::execute).toVersionList().map(Version::id))
    }

    /**
     * Sets the [outputDirectory] property.
     *
     * @param outputDirectory the file object, interpreted with [Project.file]
     */
    fun outputDirectory(outputDirectory: Any) {
        this.outputDirectory.set(project.file(outputDirectory))
    }

    /**
     * Sets the [cacheDirectory] property.
     *
     * @param cacheDirectory the file object, interpreted with [Project.file]
     */
    fun cacheDirectory(cacheDirectory: Any) {
        this.cacheDirectory.set(project.file(cacheDirectory))
    }

    /**
     * Sets the [relaxedCache] property.
     *
     * @param relaxedCache the relaxed cache flag
     */
    fun relaxedCache(relaxedCache: Boolean) {
        this.relaxedCache.set(relaxedCache)
    }

    /**
     * Sets the [platform] property.
     *
     * @param platform the platform
     */
    fun platform(platform: PlatformTristate) {
        this.platform.set(platform)
    }

    /**
     * Sets the [platform] property.
     *
     * @param platform the platform as a string
     */
    fun platform(platform: String) {
        this.platform.set(PlatformTristate.valueOf(platform.uppercase()))
    }

    /**
     * Sets the [basePackage] property.
     *
     * @param basePackage the base package
     */
    fun basePackage(basePackage: String) {
        this.basePackage.set(basePackage)
    }

    /**
     * Sets the [codeLanguage] property.
     *
     * @param codeLanguage the language flavor
     */
    fun codeLanguage(codeLanguage: CodeLanguage) {
        this.codeLanguage.set(codeLanguage)
    }

    /**
     * Sets the [codeLanguage] property.
     *
     * @param codeLanguage the language flavor as a string
     */
    fun codeLanguage(codeLanguage: String) {
        this.codeLanguage.set(CodeLanguage.valueOf(codeLanguage.uppercase()))
    }

    /**
     * Sets the [accessorType] property.
     *
     * @param accessorType the accessor flavor
     */
    fun accessorType(accessorType: AccessorType) {
        this.accessorType.set(accessorType)
    }

    /**
     * Sets the [accessorType] property.
     *
     * @param accessorType the accessor flavor as a string
     */
    fun accessorType(accessorType: String) {
        this.accessorType.set(AccessorType.valueOf(accessorType.uppercase()))
    }

    /**
     * Adds new namespaces to the [accessedNamespaces] property.
     *
     * @param accessedNamespaces the namespaces
     */
    fun accessedNamespaces(vararg accessedNamespaces: String) {
        this.accessedNamespaces.addAll(*accessedNamespaces)
    }

    /**
     * Sets the [historyNamespaces] property.
     *
     * @param historyNamespaces the history namespaces
     */
    fun historyNamespaces(vararg historyNamespaces: String) {
        this.historyNamespaces.addAll(*historyNamespaces)
    }

    /**
     * Sets the [historyIndexNamespace] property.
     *
     * @param historyIndexNamespace the index namespace, can be null
     */
    fun historyIndexNamespace(historyIndexNamespace: String?) {
        this.historyIndexNamespace.set(historyIndexNamespace)
    }

    /**
     * Creates a new accessor model with the supplied name.
     *
     * @param name the mapped class name or a glob pattern
     * @return the mapped class name ([name]), use this to refer to this class elsewhere
     */
    fun mapClass(name: String): String = mapClass(name) {}

    /**
     * Creates a new accessor model with the supplied name.
     *
     * @param name the mapped class name or a glob pattern
     * @param block the builder action
     * @return the mapped class name ([name]), use this to refer to this class elsewhere
     */
    fun mapClass(name: String, block: Action<GradleFlavoredClassAccessorBuilder>): String {
        accessors.add(
            GradleFlavoredClassAccessorBuilder(name)
                .apply(block::execute)
                .toClassAccessor()
        )

        return name
    }
}

/**
 * A three-way choice of mappings.
 *
 * @property wantsClient whether this choice wants client mappings
 * @property wantsServer whether this choice wants server mappings
 * @author Matouš Kučera
 */
enum class PlatformTristate(val wantsClient: Boolean, val wantsServer: Boolean) {
    /**
     * Client and server mappings.
     */
    CLIENT_SERVER(true, true),

    /**
     * Client mappings.
     */
    CLIENT(true, false),

    /**
     * Server mappings.
     */
    SERVER(false, true)
}

/**
 * A [ClassAccessorBuilder] with Gradle-specific enhancements.
 *
 * @param name mapped name of the accessed class
 * @author Matouš Kučera
 */
class GradleFlavoredClassAccessorBuilder(name: String) : AbstractClassAccessorBuilder(name) {
    /**
     * Adds a new chained field accessor model.
     *
     * @param block the builder action
     */
    fun fieldChain(block: Action<FieldChainBuilder>) {
        fieldChain0(block::execute)
    }

    /**
     * Adds a new chained method accessor model.
     *
     * @param block the builder action
     */
    fun methodChain(block: Action<MethodChainBuilder>) {
        methodChain0(block::execute)
    }
}
