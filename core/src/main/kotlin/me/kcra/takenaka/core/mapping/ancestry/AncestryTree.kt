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
import me.kcra.takenaka.core.mapping.MappingsMap
import me.kcra.takenaka.core.mapping.dstNamespaceIds
import me.kcra.takenaka.core.util.entryOf
import net.fabricmc.mappingio.tree.MappingTreeView.*

/**
 * A name and descriptor pair.
 */
typealias NameDescriptorPair = Pair<String, String>

/**
 * A mapping ancestry tree.
 *
 * @param nodes the ancestry nodes
 * @property allowedNamespaces namespace IDs used for computing history, distinguished by version
 * @param T the kind of the traced element; can be a class, method, field, ...
 * @author Matouš Kučera
 */
class AncestryTree<T : ElementMappingView>(nodes: List<Node<T>>, val allowedNamespaces: Map<Version, Array<Int>>) : List<AncestryTree.Node<T>> by nodes {
    /**
     * A node in the ancestry tree, immutable.
     * This represents one element (class, field, method) in multiple versions.
     *
     * @property tree the tree that this node belongs to
     * @param delegate the map that operations are delegated to
     * @property first the first version mapping (oldest)
     * @property last the last version mapping (newest)
     * @param T the kind of the traced element; can be a class, method, field, ...
     */
    class Node<T : ElementMappingView>(
        val tree: AncestryTree<T>,
        delegate: Map<Version, T>,
        val first: Map.Entry<Version, T> = delegate.entries.first(),
        val last: Map.Entry<Version, T> = delegate.entries.last()
    ) : Map<Version, T> by delegate {
        /**
         * Internal cache of names (as per the tree's allowed namespaces) of the last entry.
         *
         * This is not designed for dealing with the names themselves, rather only for set disjointness.
         */
        internal val lastNames: Set<Any> by lazy {
            val (lastVersion, lastMapping) = last

            if (lastMapping is MemberMappingView) {
                tree.allowedNamespaces[lastVersion]
                    ?.mapNotNullTo(mutableSetOf()) { ns ->
                        val name = lastMapping.getDstName(ns)
                            ?: return@mapNotNullTo null
                        val desc = lastMapping.getDstDesc(ns)
                            ?: return@mapNotNullTo null

                        name to desc
                    }
                    ?: error("Version $lastVersion is not mapped in parent tree")
            } else {
                tree.allowedNamespaces[lastVersion]
                    ?.mapNotNullTo(mutableSetOf(), lastMapping::getDstName)
                    ?: error("Version $lastVersion is not mapped in parent tree")
            }
        }
    }

    /**
     * Tries to find a node with [key] as a mapping in the last mapped version.
     *
     * @param key the mapping; [String] for classes, [NameDescriptorPair] for members (field, method)
     * @return the node, null if not found
     */
    operator fun get(key: Any): Node<T>? = find { key in it.lastNames }
}

/**
 * A mapping ancestry tree builder.
 *
 * @param T the kind of the traced element; can be a class, method, field, ...
 * @author Matouš Kučera
 */
class AncestryTreeBuilder<T : ElementMappingView> {
    /**
     * The ancestry nodes, mutable.
     */
    val nodes = mutableListOf<MutableNode<T>>()

    /**
     * Namespace IDs used for computing history, distinguished by version.
     */
    val allowedNamespaces = mutableMapOf<Version, Array<Int>>()

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
     * Makes the builder inherit allowed namespaces from another tree.
     *
     * @param tree the tree to be inherited from
     */
    fun inheritNamespaces(tree: AncestryTree<*>) {
        allowedNamespaces += tree.allowedNamespaces
    }

    /**
     * Makes an immutable ancestry tree out of this builder.
     *
     * @return the ancestry tree
     */
    fun toAncestryTree(): AncestryTree<T> {
        val immutableNodes = mutableListOf<AncestryTree.Node<T>>()
        return AncestryTree(immutableNodes, allowedNamespaces).apply {
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
    class MutableNode<T : ElementMappingView>(internal val delegate: MutableMap<Version, T> = mutableMapOf()) : MutableMap<Version, T> by delegate {
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
inline fun <T : ElementMappingView> buildAncestryTree(block: AncestryTreeBuilder<T>.() -> Unit): AncestryTree<T> =
    AncestryTreeBuilder<T>().apply(block).toAncestryTree()

/**
 * Computes an ancestry tree of all classes in the supplied versions.
 *
 * @param mappings the joined version mapping files
 * @param allowedNamespaces namespaces that are used in this tree for tracing history, not distinguished by version; empty if all namespaces should be considered
 * @return the ancestry tree
 */
fun classAncestryTreeOf(mappings: MappingsMap, allowedNamespaces: List<String> = emptyList()): AncestryTree<ClassMappingView> = buildAncestryTree {
    // convert to sorted map to ensure proper ordering
    mappings.toSortedMap().forEach { (version, tree) ->
        val treeAllowedNamespaces = allowedNamespaces
            .map(tree::getNamespaceId)
            .filter { it != NULL_NAMESPACE_ID }
            .ifEmpty { tree.dstNamespaceIds.toList() }
            .toTypedArray()

        this@buildAncestryTree.allowedNamespaces[version] = treeAllowedNamespaces

        tree.classes.forEach { klass ->
            val classMappings = treeAllowedNamespaces.mapNotNullTo(mutableSetOf(), klass::getDstName)
            val classMappingsArray = classMappings.toTypedArray() // perf: use array due to marginally better iteration performance

            // do we have a node with one or more equal names in the last version?
            // if we don't, make a new node and append it to the tree
            val node = findOrEmpty { node ->
                val (lastVersion, lastMapping) = node.last
                val lastNames = (node.lastNames ?: this@buildAncestryTree.allowedNamespaces[lastVersion]?.mapNotNull(lastMapping::getDstName))
                    ?: error("Version ${version.id} has not been mapped yet, make sure mappings are sorted correctly")

                return@findOrEmpty classMappingsArray.any(lastNames::contains)
            }

            // add the entry for this version to the node
            val lastValue = node.put(version, klass)
            if (lastValue == null) {
                node.lastNames = classMappings
            }
        }
    }
}

/**
 * Computes an ancestry tree of all fields in the supplied class ancestry node.
 *
 * @param klass the class node
 * @return the ancestry tree
 */
fun fieldAncestryTreeOf(klass: AncestryTree.Node<ClassMappingView>): AncestryTree<FieldMappingView> = buildAncestryTree {
    inheritNamespaces(klass.tree)

    klass.forEach { (version, realKlass) ->
        val treeAllowedNamespaces = klass.tree.allowedNamespaces[version]
            ?: error("Version ${version.id} has not been mapped yet")

        realKlass.fields.forEach { field ->
            val fieldMappings = treeAllowedNamespaces
                .mapNotNullTo(mutableSetOf()) { ns ->
                    val name = field.getDstName(ns)
                        ?: return@mapNotNullTo null
                    val desc = field.getDstDesc(ns)
                        ?: return@mapNotNullTo null

                    name to desc
                }

            val fieldMappingsArray = fieldMappings.toTypedArray() // perf: use array due to marginally better iteration performance

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = findOrEmpty { node ->
                val (lastVersion, lastMapping) = node.last
                val lastNames = node.lastNames
                    ?: this@buildAncestryTree.allowedNamespaces[lastVersion]?.mapNotNull { ns ->
                        val name = lastMapping.getDstName(ns)
                            ?: return@mapNotNull null
                        val desc = lastMapping.getDstDesc(ns)
                            ?: return@mapNotNull null

                        name to desc
                    }
                    ?: error("Version ${lastVersion.id} has not been mapped yet")

                return@findOrEmpty fieldMappingsArray.any(lastNames::contains)
            }

            // add the entry for this version to the node
            val lastValue = node.put(version, field)
            if (lastValue == null) {
                node.lastNames = fieldMappings
            }
        }
    }
}

/**
 * Computes an ancestry tree of all methods in the supplied class ancestry node.
 *
 * @param klass the class node
 * @return the ancestry tree
 */
fun methodAncestryTreeOf(klass: AncestryTree.Node<ClassMappingView>): AncestryTree<MethodMappingView> = buildAncestryTree {
    inheritNamespaces(klass.tree)

    klass.forEach { (version, realKlass) ->
        val treeAllowedNamespaces = klass.tree.allowedNamespaces[version]
            ?: error("Version ${version.id} has not been mapped yet")

        realKlass.methods.forEach { method ->
            val methodMappings = treeAllowedNamespaces
                .mapNotNullTo(mutableSetOf()) { ns ->
                    val name = method.getDstName(ns)
                        ?: return@mapNotNullTo null
                    val desc = method.getDstDesc(ns)
                        ?: return@mapNotNullTo null

                    name to desc
                }

            val methodMappingsArray = methodMappings.toTypedArray() // perf: use array due to marginally better iteration performance

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = findOrEmpty { node ->
                val (lastVersion, lastMapping) = node.last
                val lastNames = node.lastNames
                    ?: this@buildAncestryTree.allowedNamespaces[lastVersion]?.mapNotNull { ns ->
                        val name = lastMapping.getDstName(ns)
                            ?: return@mapNotNull null
                        val desc = lastMapping.getDstDesc(ns)
                            ?: return@mapNotNull null

                        name to desc
                    }
                    ?: error("Version ${lastVersion.id} has not been mapped yet")

                return@findOrEmpty methodMappingsArray.any(lastNames::contains)
            }

            // add the entry for this version to the node
            val lastValue = node.put(version, method)
            if (lastValue == null) {
                node.lastNames = methodMappings
            }
        }
    }
}
