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

package me.kcra.takenaka.core.mapping.ancestry

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.util.entryOf
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * A mapping ancestry tree builder.
 *
 * @param T the kind of the traced element; can be a class, method, field, ...
 * @author Matouš Kučera
 */
class AncestryTreeBuilder<T : MappingTreeView.ElementMappingView> {
    /**
     * The ancestry nodes, mutable.
     */
    val nodes = mutableListOf<MutableNode<T>>()

    /**
     * Mapping trees used for composition of this ancestry tree, distinguished by version.
     */
    val trees = mutableMapOf<Version, MappingTreeView>()

    /**
     * Namespace IDs used for computing node indices, distinguished by version.
     */
    val indexNamespaces = mutableMapOf<Version, Int>()

    /**
     * Namespace IDs used for computing history, distinguished by version.
     */
    val allowedNamespaces = mutableMapOf<Version, Array<Int>>()

    /**
     * Lookup for nodes by their indices.
     */
    val indices = mutableMapOf<Int, MutableNode<T>>()

    /**
     * Gets a node by its index, creating a new one if not found.
     *
     * @param index the index
     * @return the node
     */
    fun findByIndex(index: Int): MutableNode<T> = indices.getOrPut(index, ::emptyNode)

    /**
     * Tries to find a node by the [block] predicate, creating a new one if not found.
     *
     * @param block the search predicate
     * @return the node
     */
    fun findOrEmpty(block: (MutableNode<T>) -> Boolean): MutableNode<T> = nodes.find(block) ?: emptyNode()

    /**
     * Creates a new empty node and appends it to the builder.
     *
     * @return the node
     */
    fun emptyNode(): MutableNode<T> = MutableNode<T>().also(nodes::add)

    /**
     * Makes the builder inherit mapping trees from another tree.
     *
     * @param tree the tree to be inherited from
     */
    fun inheritTrees(tree: AncestryTree<*>) {
        trees += tree.trees
    }

    /**
     * Makes the builder inherit allowed and index namespaces from another tree.
     *
     * @param tree the tree to be inherited from
     */
    fun inheritNamespaces(tree: AncestryTree<*>) {
        indexNamespaces += tree.indexNamespaces
        allowedNamespaces += tree.allowedNamespaces
    }

    /**
     * Makes an immutable ancestry tree out of this builder.
     *
     * @return the ancestry tree
     */
    fun toAncestryTree(): AncestryTree<T> {
        val immutableNodes = mutableListOf<AncestryTree.Node<T>>()
        return AncestryTree(immutableNodes, trees, indexNamespaces, allowedNamespaces).apply {
            immutableNodes += nodes.map { AncestryTree.Node(this, it, it.first, it.last) }
        }
    }

    /**
     * A node in the ancestry tree, mutable.
     *
     * @property delegate the map that operations are delegated to
     * @param T the kind of the traced element; can be a class, method, field, ...
     * @see AncestryTree.Node
     */
    class MutableNode<T : MappingTreeView.ElementMappingView>(internal val delegate: MutableMap<Version, T> = mutableMapOf()) : MutableMap<Version, T> by delegate {
        /**
         * The first version mapping (oldest).
         */
        lateinit var first: Map.Entry<Version, T>

        /**
         * The last version mapping (newest).
         */
        lateinit var last: Map.Entry<Version, T>

        /**
         * Internal cache of names (as per the tree's allowed namespaces) of the last entry.
         *
         * **This is not set by the [put] methods.**
         */
        internal var lastNames: Set<Any>? = null

        init {
            if (delegate.isNotEmpty()) {
                first = delegate.entries.first()
                last = delegate.entries.last()
            }
        }

        override fun put(key: Version, value: T): T? {
            val oldValue = delegate.put(key, value)

            if (!::first.isInitialized) {
                first = entryOf(key, value)
            }
            if (oldValue == null) {
                last = entryOf(key, value)
            }

            return oldValue
        }
    }
}

/**
 * Builds an ancestry tree from a builder.
 *
 * @param block the builder action
 * @param T the kind of the traced element; can be a class, method, field, ...
 * @return the ancestry tree
 */
inline fun <T : MappingTreeView.ElementMappingView> buildAncestryTree(block: AncestryTreeBuilder<T>.() -> Unit): AncestryTree<T> =
    AncestryTreeBuilder<T>().apply(block).toAncestryTree()