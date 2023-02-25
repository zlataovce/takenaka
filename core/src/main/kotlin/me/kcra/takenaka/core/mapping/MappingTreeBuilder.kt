package me.kcra.takenaka.core.mapping

import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree

/**
 * A function that mutates a mapping tree.
 */
typealias TreeMutator = MappingTree.() -> Unit

/**
 * A mapping tree builder.
 *
 * @author Matouš Kučera
 */
class MappingTreeBuilder {
    /**
     * The mapping contributors.
     */
    val contributors: MutableList<MappingContributor> = mutableListOf()

    /**
     * Functions that mutate the finalized tree, maintains insertion order.
     */
    val mutators: MutableList<TreeMutator> = mutableListOf()

    /**
     * Appends mapping contributors.
     *
     * @param items the contributors
     */
    fun contributor(vararg items: MappingContributor) {
        contributors += items
    }

    /**
     * Appends mapping contributors.
     *
     * @param items the contributors
     */
    fun contributor(items: List<MappingContributor>) {
        contributors += items
    }

    /**
     * Appends a mapping contributor, wrapped with [wrap].
     *
     * @param item the contributor
     * @param wrap the wrapping function
     */
    fun contributor(item: MappingContributor, wrap: VisitorWrapper) {
        contributors += WrappingContributor(item, wrap)
    }

    /**
     * Appends a new tree mutating function.
     *
     * @param block the mutator
     */
    fun mutate(block: TreeMutator) {
        mutators += block
    }

    /**
     * Builds the mapping tree.
     *
     * @return the mapping tree
     */
    fun toMappingTree(): MappingTree = MemoryMappingTree().apply {
        contributors.forEach { it.accept(this) }
        mutators.forEach { it(this) }
    }
}

inline fun buildMappingTree(block: MappingTreeBuilder.() -> Unit): MappingTree = MappingTreeBuilder().apply(block).toMappingTree()
