package me.kcra.takenaka.core.mapping

import net.fabricmc.mappingio.MappingVisitor

/**
 * A mapping contributor.
 *
 * @author Matouš Kučera
 */
interface MappingContributor {
    /**
     * The target namespace of the contributor's mappings.
     */
    val targetNamespace: String

    /**
     * Visits its mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    fun accept(visitor: MappingVisitor)
}