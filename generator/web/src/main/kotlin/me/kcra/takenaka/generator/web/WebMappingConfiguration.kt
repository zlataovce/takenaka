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

package me.kcra.takenaka.generator.web

import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.mapping.InterceptAfter
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.generator.common.ContributorProvider
import me.kcra.takenaka.generator.common.MappingConfiguration
import me.kcra.takenaka.generator.common.MappingConfigurationBuilder
import me.kcra.takenaka.generator.common.WorkspacePathProvider
import me.kcra.takenaka.generator.web.transformers.Transformer
import net.fabricmc.mappingio.MappingUtil

/**
 * Configuration for [WebGenerator].
 *
 * @property versions the mapping candidate versions
 * @property workspace the mapping cache workspace
 * @property contributorProvider a function that provides mapping contributors based on a version
 * @property mapperInterceptor a function that modifies every mapping tree, useful for normalization and correction
 * @property joinedOutputProvider the joined mapping file path provider, returns null if it should not be persisted (rebuilt in memory every run)
 * @property transformers a list of transformers that transform the output
 * @property namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @property namespaces a map of namespaces and their descriptions, unspecified namespaces will not be shown
 * @property index a resolver for foreign class references
 * @property craftBukkitVersionReplaceCandidates namespaces that should have [replaceCraftBukkitNMSVersion] applied (most likely Spigot mappings or a flavor of them)
 * @property historicalNamespaces namespaces that should be used for computing history, namespaces from [namespaceFriendlinessIndex] are considered by default (excluding the obfuscated one)
 * @author Matouš Kučera
 */
class WebMappingConfiguration(
    versions: List<String>,
    workspace: CompositeWorkspace,
    contributorProvider: ContributorProvider,
    mapperInterceptor: InterceptAfter,
    joinedOutput: WorkspacePathProvider,
    val transformers: List<Transformer> = emptyList(),
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val namespaces: Map<String, NamespaceDescription> = emptyMap(),
    val index: ClassSearchIndex = emptyClassSearchIndex(),
    val craftBukkitVersionReplaceCandidates: List<String> = emptyList(),
    val historicalNamespaces: List<String> = namespaceFriendlinessIndex - MappingUtil.NS_SOURCE_FALLBACK,
) : MappingConfiguration(
    versions,
    workspace,
    contributorProvider,
    mapperInterceptor,
    joinedOutput
)

/**
 * A builder for [WebMappingConfiguration].
 *
 * @author Matouš Kučera
 */
class WebMappingConfigurationBuilder : MappingConfigurationBuilder() {
    /**
     * Transformers that transform the output.
     */
    var transformers = mutableListOf<Transformer>()

    /**
     * An ordered list of namespaces that will be considered when selecting a "friendly" name.
     */
    var namespaceFriendlinessIndex = mutableListOf<String>()

    /**
     * A map of namespaces and their descriptions, unspecified namespaces will not be shown on the documentation.
     */
    var namespaces = mutableMapOf<String, NamespaceDescription>()

    /**
     * A resolver for foreign class references, defaults to a no-op.
     */
    var index: ClassSearchIndex = emptyClassSearchIndex()

    /**
     * Namespaces that should have [replaceCraftBukkitNMSVersion] applied (most likely Spigot mappings or a flavor of them).
     */
    var craftBukkitVersionReplaceCandidates = mutableListOf<String>()

    /**
     * Namespaces that should be used for computing history, empty if namespaces from [namespaceFriendlinessIndex] should be considered (excluding the obfuscated one).
     */
    var historicalNamespaces = mutableListOf<String>()

    /**
     * Appends transformers.
     *
     * @param items the transformers
     */
    fun transformer(vararg items: Transformer) {
        transformers += items
    }

    /**
     * Appends transformers.
     *
     * @param items the transformers
     */
    fun transformer(items: List<Transformer>) {
        transformers += items
    }

    /**
     * Appends friendly namespaces to the index.
     *
     * @param items the friendly namespaces
     */
    fun namespaceFriendliness(vararg items: String) {
        namespaceFriendlinessIndex += items
    }

    /**
     * Appends friendly namespaces to the index.
     *
     * @param items the friendly namespaces
     */
    fun namespaceFriendliness(items: List<String>) {
        namespaceFriendlinessIndex += items
    }

    /**
     * Appends a namespace.
     *
     * @param name the original name, as in the mapping tree
     * @param friendlyName the friendly name, this will be shown in the documentation
     * @param color the CSS-compatible color, this will be shown in the documentation
     * @param license the license reference
     */
    fun namespace(name: String, friendlyName: String, color: String, license: LicenseReference? = null) {
        namespaces += name to NamespaceDescription(friendlyName, color, license)
    }

    /**
     * Appends a namespace.
     *
     * @param name the original name, as in the mapping tree
     * @param friendlyName the friendly name, this will be shown in the documentation
     * @param color the CSS-compatible color, this will be shown in the documentation
     * @param licenseContentKey the license content metadata key in the mapping tree
     * @param licenseSourceKey the license source metadata key in the mapping tree
     */
    fun namespace(name: String, friendlyName: String, color: String, licenseContentKey: String, licenseSourceKey: String = "${licenseContentKey}_source") {
        namespaces += name to NamespaceDescription(friendlyName, color, LicenseReference(licenseContentKey, licenseSourceKey))
    }

    /**
     * Replaces the class search index.
     *
     * @param items the indexers
     */
    fun index(vararg items: ClassSearchIndex) {
        index(items.toList())
    }

    /**
     * Replaces the class search index.
     *
     * @param items the indexers
     */
    fun index(items: List<ClassSearchIndex>) {
        if (items.isNotEmpty()) {
            index = if (items.size == 1) items[0] else CompositeClassSearchIndex(items)
        }
    }

    /**
     * Builds a mapping configuration out of this builder.
     *
     * @return the configuration
     */
    override fun toMappingConfig(): WebMappingConfiguration = WebMappingConfiguration(
        versions,
        mappingWorkspace,
        contributorProvider,
        mapperInterceptor,
        joinedOutputProvider,
        transformers,
        namespaceFriendlinessIndex,
        namespaces,
        index,
        craftBukkitVersionReplaceCandidates,
        historicalNamespaces.ifEmpty { namespaceFriendlinessIndex - MappingUtil.NS_SOURCE_FALLBACK }
    )
}

/**
 * Builds a mapping configuration with a builder.
 *
 * @param block the builder action
 * @return the configuration
 */
inline fun buildWebMappingConfig(block: WebMappingConfigurationBuilder.() -> Unit): WebMappingConfiguration =
    WebMappingConfigurationBuilder().apply(block).toMappingConfig()
