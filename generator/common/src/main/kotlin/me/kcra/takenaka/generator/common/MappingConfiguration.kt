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
 * @author Matouš Kučera
 */
open class MappingConfiguration(
    val versions: List<String>,
    val workspace: CompositeWorkspace,
    val contributorProvider: ContributorProvider,
    val mapperInterceptor: InterceptAfter
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
    open fun toMappingConfig(): MappingConfiguration =
        MappingConfiguration(versions, mappingWorkspace, contributorProvider, mapperInterceptor)
}

/**
 * Builds a mapping configuration with a builder.
 *
 * @param block the builder action
 * @return the configuration
 */
inline fun buildMappingConfig(block: MappingConfigurationBuilder.() -> Unit): MappingConfiguration =
    MappingConfigurationBuilder().apply(block).toMappingConfig()
