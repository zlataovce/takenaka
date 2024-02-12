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

package me.kcra.takenaka.generator.common.provider.impl

import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.MapperIntercept
import me.kcra.takenaka.core.mapping.MappingContributor
import java.nio.file.Path
import kotlin.properties.Delegates

/**
 * A function for providing a list of mapping contributors for a single version.
 */
typealias ContributorProvider = (VersionedWorkspace) -> List<MappingContributor>

/**
 * A function for providing a path from a versioned workspace.
 */
typealias WorkspacePathProvider = (VersionedWorkspace) -> Path?

/**
 * A configuration class for [ResolvingMappingProvider].
 *
 * @property versions the mapping candidate versions
 * @property workspace the mapping cache workspace
 * @property contributorProvider a function that provides mapping contributors based on a version
 * @property interceptors functions that sequentially wrap a tree visitor before any mappings are visited to it, useful for simple filtering
 * @property joinedOutputProvider the joined mapping file path provider, returns null if it should not be persisted (rebuilt in memory every run)
 * @author Matouš Kučera
 */
data class MappingConfiguration(
    val versions: List<String>,
    val workspace: CompositeWorkspace,
    val contributorProvider: ContributorProvider,
    val interceptors: List<MapperIntercept>,
    val joinedOutputProvider: WorkspacePathProvider
)

/**
 * A [MappingConfiguration] builder.
 *
 * @author Matouš Kučera
 */
class MappingConfigurationBuilder {
    /**
     * The mapping candidate versions.
     */
    var versions = mutableListOf<String>()

    /**
     * The mapping cache workspace.
     */
    var mappingWorkspace by Delegates.notNull<CompositeWorkspace>()

    /**
     * A function that provides mapping contributors based on a version.
     */
    var contributorProvider by Delegates.notNull<ContributorProvider>()

    /**
     * Functions that sequentially wrap a tree visitor before any mappings are visited to it, useful for simple filtering.
     */
    var interceptors = mutableListOf<MapperIntercept>()

    /**
     * The joined mapping file path provider, returns null if it should not be persisted (rebuilt in memory every run).
     */
    var joinedOutputProvider: WorkspacePathProvider = { workspace -> workspace["joined.tiny"] }

    /**
     * Appends versions.
     *
     * @param items the versions
     */
    fun version(vararg items: String) {
        versions += items
    }

    /**
     * Appends versions.
     *
     * @param items the versions
     */
    fun version(items: List<String>) {
        versions += items
    }

    /**
     * Sets [mappingWorkspace].
     *
     * @param value the workspace
     */
    fun workspace(value: Workspace) {
        mappingWorkspace = value.asComposite()
    }

    /**
     * Sets [contributorProvider].
     *
     * @param block the provider
     */
    fun contributors(block: ContributorProvider) {
        contributorProvider = block
    }

    /**
     * Sets [joinedOutputProvider].
     *
     * @param block the provider
     */
    fun joinedOutputPath(block: WorkspacePathProvider) {
        joinedOutputProvider = block
    }

    /**
     * Appends to [interceptors].
     *
     * @param block the interceptor
     */
    fun intercept(block: MapperIntercept) {
        interceptors += block
    }

    /**
     * Builds a mapping configuration out of this builder.
     *
     * @return the configuration
     */
    fun toMappingConfig() = MappingConfiguration(
        versions,
        mappingWorkspace,
        contributorProvider,
        interceptors,
        joinedOutputProvider
    )
}

/**
 * Builds a mapping configuration with a builder.
 *
 * @param block the builder action
 * @return the configuration
 */
inline fun buildMappingConfig(block: MappingConfigurationBuilder.() -> Unit): MappingConfiguration =
    MappingConfigurationBuilder().apply(block).toMappingConfig()
