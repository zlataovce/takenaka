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
import me.kcra.takenaka.core.mapping.VersionedMappingMap
import me.kcra.takenaka.core.util.firstEntryUnsafe
import me.kcra.takenaka.core.util.lastEntryUnsafe
import net.fabricmc.mappingio.tree.MappingTreeView.*
import java.util.Collections

/**
 * A mapping ancestry tree.
 *
 * @param nodes the ancestry nodes
 * @property allowedNamespaces namespace IDs used for computing history, distinguished by version
 * @author Matouš Kučera
 */
class AncestryTree<T : ElementMappingView>(nodes: List<Node<T>>, val allowedNamespaces: Map<Version, List<Int>>) : List<AncestryTree.Node<T>> by nodes {
    /**
     * A node in the ancestry tree.
     * This represents one element (class, field, method) in multiple versions.
     *
     * @param tree the tree that this node belongs to
     * @property delegate the map that operations are delegated to
     */
    class Node<T : ElementMappingView>(val tree: AncestryTree<T>, internal val delegate: Map<Version, T>) : Map<Version, T> by delegate {
        /**
         * Returns the first inserted version mapping (newest).
         *
         * @return the version entry
         */
        fun first() = delegate.firstEntryUnsafe()

        /**
         * Returns the last inserted version mapping (oldest).
         *
         * @return the version entry
         */
        fun last() = delegate.lastEntryUnsafe()
    }

    /**
     * Tries to find a node with [key] as a mapping in the last mapped version.
     *
     * @param key the mapping
     * @return the node, null if not found
     */
    operator fun get(key: String): Node<T>? = find { node ->
        val (lastVersion, lastMapping) = node.first()
        val lastNames = allowedNamespaces[lastVersion]?.mapNotNull(lastMapping::getDstName)
            ?: return@find false

        return@find lastNames.any(key::equals)
    }
}

/**
 * A mapping ancestry tree builder.
 *
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
    val allowedNamespaces = mutableMapOf<Version, List<Int>>()

    /**
     * Tries to find a node by the [block] predicate, creating a new one if not found.
     *
     * @param block the search predicate
     * @return the node
     */
    fun resolveNode(block: (MutableNode<T>) -> Boolean): MutableNode<T> = nodes.find(block) ?: emptyNode()

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
            immutableNodes += nodes.map { AncestryTree.Node(this, it) }
        }
    }

    /**
     * A node in the ancestry tree, mutable.
     *
     * @property delegate the map that operations are delegated to
     * @see AncestryTree.Node
     */
    class MutableNode<T : ElementMappingView>(internal val delegate: MutableMap<Version, T> = mutableMapOf()) : MutableMap<Version, T> by delegate {
        /**
         * Returns the first inserted version mapping (newest).
         *
         * @return the version entry
         */
        fun first() = delegate.firstEntryUnsafe()

        /**
         * Returns the last inserted version mapping (oldest).
         *
         * @return the version entry
         */
        fun last() = delegate.lastEntryUnsafe()
    }
}

/**
 * Builds an ancestry tree from a builder.
 *
 * @param block the builder action
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
fun classAncestryTreeOf(mappings: VersionedMappingMap, allowedNamespaces: List<String> = emptyList()): AncestryTree<ClassMappingView> = buildAncestryTree {
    mappings.forEach { (version, tree) ->
        val treeAllowedNamespaces = allowedNamespaces
            .map(tree::getNamespaceId)
            .filter { it != NULL_NAMESPACE_ID }
            .ifEmpty { (0 until tree.maxNamespaceId).toList() }

        this@buildAncestryTree.allowedNamespaces[version] = treeAllowedNamespaces

        tree.classes.forEach { klass ->
            val classMappings = treeAllowedNamespaces.mapNotNull(klass::getDstName)
            // do we have a node with one or more equal names in the last version?
            // if we don't, make a new node and append it to the tree
            val node = resolveNode { node ->
                val (lastVersion, lastMapping) = node.last()
                val lastNames = this@buildAncestryTree.allowedNamespaces[lastVersion]?.mapNotNull(lastMapping::getDstName)
                    ?: error("Version ${version.id} has not been mapped yet, make sure mappings are sorted correctly")

                return@resolveNode !Collections.disjoint(lastNames, classMappings)
            }

            // add the entry for this version to the node
            node[version] = klass
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
                .map { field.getDstName(it) to field.getDstDesc(it) }
                .filter { it.first != null && it.second != null }

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = resolveNode { node ->
                val (lastVersion, lastMapping) = node.last()
                val lastNames = klass.tree.allowedNamespaces[lastVersion]
                    ?.map { lastMapping.getDstName(it) to lastMapping.getDstDesc(it) }
                    ?.filter { it.first != null && it.second != null }
                    ?: error("Version ${version.id} has not been mapped yet")

                return@resolveNode !Collections.disjoint(lastNames, fieldMappings)
            }

            // add the entry for this version to the node
            node[version] = field
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
                .map { method.getDstName(it) to method.getDstDesc(it) }
                .filter { it.first != null && it.second != null }

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = resolveNode { node ->
                val (lastVersion, lastMapping) = node.last()
                val lastNames = klass.tree.allowedNamespaces[lastVersion]
                    ?.map { lastMapping.getDstName(it) to lastMapping.getDstDesc(it) }
                    ?.filter { it.first != null && it.second != null }
                    ?: error("Version ${version.id} has not been mapped yet")

                return@resolveNode !Collections.disjoint(lastNames, methodMappings)
            }

            // add the entry for this version to the node
            node[version] = method
        }
    }
}
