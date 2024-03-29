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

package me.kcra.takenaka.core.mapping.ancestry

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.util.entryOf
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * A mapping ancestry tree builder.
 *
 * @param T the type of the mapping tree
 * @param E the kind of the traced element; can be a class, method, field, ...
 * @author Matouš Kučera
 */
class AncestryTreeBuilder<T : MappingTreeView, E : MappingTreeView.ElementMappingView> {
    /**
     * The ancestry nodes, mutable.
     */
    val nodes = mutableListOf<MutableNode<E>>()

    /**
     * Mapping trees used for composition of this ancestry tree, distinguished by version.
     */
    val trees = mutableMapOf<Version, T>()

    /**
     * Namespaces used for computing node indices, distinguished by version.
     */
    val indexNamespaces = mutableMapOf<Version, ResolvedNamespace>()

    /**
     * Namespaces used for computing history, distinguished by version.
     */
    val allowedNamespaces = mutableMapOf<Version, Array<ResolvedNamespace>>()

    /**
     * Lookup for nodes by their indices.
     */
    val indices = mutableMapOf<Int, MutableNode<E>>()

    /**
     * Whether ancestry nodes should be put into [nodeBuffer] instead of directly into [nodes].
     */
    var buffering = false

    /**
     * A buffer of ancestry nodes that have been added in this cycle.
     */
    private val nodeBuffer = mutableListOf<MutableNode<E>>()

    /**
     * Collection that new nodes should be appended to.
     */
    private inline val currentNodes: MutableCollection<MutableNode<E>>
        get() = if (buffering) nodeBuffer else nodes

    /**
     * Gets a node by its index, creating a new one if not found.
     *
     * @param index the index
     * @return the node
     */
    fun findByIndex(index: Int): MutableNode<E> = indices.getOrPut(index, ::emptyNode)

    /**
     * Tries to find a node by the [block] predicate, creating a new one if not found.
     *
     * @param block the search predicate
     * @return the node
     */
    inline fun findOrEmpty(block: (MutableNode<E>) -> Boolean): MutableNode<E> = nodes.find(block) ?: emptyNode()

    /**
     * Creates a new empty node and appends it to the builder.
     *
     * @return the node
     */
    fun emptyNode(): MutableNode<E> = MutableNode<E>().also(currentNodes::add)

    /**
     * Makes the builder inherit mapping trees from another tree.
     *
     * @param tree the tree to be inherited from
     */
    fun inheritTrees(tree: AncestryTree<T, *>) {
        trees += tree.trees
    }

    /**
     * Makes the builder inherit allowed and index namespaces from another tree.
     *
     * @param tree the tree to be inherited from
     */
    fun inheritNamespaces(tree: AncestryTree<*, *>) {
        indexNamespaces += tree.indexNamespaces
        allowedNamespaces += tree.allowedNamespaces
    }

    /**
     * Makes an immutable ancestry tree out of this builder.
     *
     * @return the ancestry tree
     */
    fun toAncestryTree(): AncestryTree<T, E> {
        flushBuffer()

        val immutableNodes = mutableListOf<AncestryTree.Node<T, E>>()
        return AncestryTree(immutableNodes, trees, indexNamespaces, allowedNamespaces).apply {
            immutableNodes += nodes.map {
                AncestryTree.Node(
                    this,
                    it.delegate, // use the delegate here to discard of the MutableNode reference
                    checkNotNull(it.first) {
                        "Node does not have a first mapping"
                    },
                    checkNotNull(it.last) {
                        "Node does not have a last mapping"
                    }
                )
            }
        }
    }

    /**
     * Buffers nodes added in [block] and flushes the buffer afterward.
     *
     * @param block the builder action
     */
    inline fun bufferCycle(block: AncestryTreeBuilder<T, E>.() -> Unit) {
        buffering = true
        block()
        buffering = false

        flushBuffer()
    }

    /**
     * Flushes [nodeBuffer] into [nodes].
     */
    fun flushBuffer() {
        nodes += nodeBuffer
        nodeBuffer.clear()
    }

    /**
     * A node in the ancestry tree, mutable.
     *
     * @property delegate the map that operations are delegated to
     * @param E the kind of the traced element; can be a class, method, field, ...
     * @see AncestryTree.Node
     */
    class MutableNode<E : MappingTreeView.ElementMappingView>(internal val delegate: MutableMap<Version, E> = mutableMapOf()) : MutableMap<Version, E> by delegate {
        /**
         * The first version mapping (oldest).
         */
        var first: Map.Entry<Version, E>? = null

        /**
         * The last version mapping (newest).
         */
        var last: Map.Entry<Version, E>? = null

        /**
         * Internal cache of names (as per the tree's allowed namespaces) of the last entry.
         *
         * **This is not set by the [put] methods.**
         */
        internal var lastNames: Set<NamespacedMapping>? = null

        /**
         * Internal cache of descriptors (as per the tree's allowed namespaces) of the last entry.
         *
         * **This is not set by the [put] methods.**
         */
        internal var lastDescs: Set<NamespacedMapping>? = null

        init {
            if (delegate.isNotEmpty()) {
                first = delegate.entries.first()
                last = delegate.entries.last()
            }
        }

        override fun put(key: Version, value: E): E? {
            val oldValue = delegate.put(key, value)

            if (first == null) {
                first = entryOf(key, value)
            }
            if (oldValue == null) {
                last = entryOf(key, value)
            }

            return oldValue
        }

        override fun putAll(from: Map<out Version, E>) {
            // replaced the putAll implementation here to route everything through the put method
            // makes dealing with the first and last items easier
            from.forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}

/**
 * Builds an ancestry tree from a builder.
 *
 * @param block the builder action
 * @param T the type of the mapping tree
 * @param E the kind of the traced element; can be a class, method, field, ...
 * @return the ancestry tree
 */
inline fun <T : MappingTreeView, E : MappingTreeView.ElementMappingView> buildAncestryTree(block: AncestryTreeBuilder<T, E>.() -> Unit): AncestryTree<T, E> =
    AncestryTreeBuilder<T, E>().apply(block).toAncestryTree()