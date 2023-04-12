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

package me.kcra.takenaka.generator.common

import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.mapping.InterceptAfter
import kotlin.properties.Delegates

/**
 * A base configuration class for generators.
 *
 * Generator implementations should prefer to subclass this,
 * instead of passing lots of parameters in constructors.
 *
 * @property versions the mapping candidate versions
 * @property workspace the mapping cache workspace
 * @property contributorProvider a function that provides mapping contributors based on a version
 * @property mapperInterceptor a function that modifies every mapping tree, useful for normalization and correction
 * @property joinedOutputProvider the joined mapping file path provider, returns null if it should not be persisted (rebuilt in memory every run)
 * @author Matouš Kučera
 */
open class MappingConfiguration(
    val versions: List<String>,
    val workspace: CompositeWorkspace,
    val contributorProvider: ContributorProvider,
    val mapperInterceptor: InterceptAfter,
    val joinedOutputProvider: WorkspacePathProvider
)

/**
 * A base [MappingConfiguration] builder class.
 *
 * @author Matouš Kučera
 */
open class MappingConfigurationBuilder {
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
     * A function that modifies every mapping tree, useful for normalization and correction.
     */
    var mapperInterceptor: InterceptAfter = {}

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
     * Sets [contributorProvider].
     *
     * @param block the provider
     */
    fun provideContributors(block: ContributorProvider) {
        contributorProvider = block
    }

    /**
     * Sets [joinedOutputProvider].
     *
     * @param block the provider
     */
    fun provideJoinedOutputPath(block: WorkspacePathProvider) {
        joinedOutputProvider = block
    }

    /**
     * Sets [mapperInterceptor].
     *
     * @param block the interceptor
     */
    fun interceptMapper(block: InterceptAfter) {
        mapperInterceptor = block
    }

    /**
     * Builds a mapping configuration out of this builder.
     *
     * @return the configuration
     */
    open fun toMappingConfig(): MappingConfiguration = MappingConfiguration(
        versions,
        mappingWorkspace,
        contributorProvider,
        mapperInterceptor,
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
