package me.kcra.takenaka.generator.common

import me.kcra.takenaka.core.mapping.MutableMappingsMap

/**
 * A class that provides a set of mappings required for generation.
 *
 * @author Matouš Kučera
 */
interface MappingProvider {
    /**
     * Provides the mappings.
     *
     * @return the mappings
     */
    suspend fun get(): MutableMappingsMap
}
