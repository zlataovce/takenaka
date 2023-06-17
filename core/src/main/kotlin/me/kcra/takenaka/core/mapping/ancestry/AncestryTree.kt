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
import me.kcra.takenaka.core.mapping.matchers.isConstructor
import me.kcra.takenaka.core.util.md5Digest
import me.kcra.takenaka.core.util.updateAndHex
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView.*

/**
 * A name and descriptor pair.
 */
typealias NameDescriptorPair = Pair<String, String>

/**
 * A mapping ancestry tree.
 *
 * @param nodes the ancestry nodes
 * @property trees mapping trees used for the computation of this ancestry tree
 * @property allowedNamespaces namespace IDs used for computing history, distinguished by version
 * @param T the kind of the traced element; can be a class, method, field, ...
 * @author Matouš Kučera
 */
class AncestryTree<T : ElementMappingView>(
    nodes: List<Node<T>>,
    val trees: MappingsMap,
    val allowedNamespaces: Map<Version, Array<Int>>
) : List<AncestryTree.Node<T>> by nodes {
    /**
     * A hash of this ancestry tree.
     *
     * This is equal to a MD5 hash of the concatenated amount of nodes
     * and the mapped version IDs, e.g. `1234,1.19.4,1.19.3`.
     */
    val hash by lazy {
        md5Digest.updateAndHex(
            mutableListOf(nodes.size.toString(10))
                .apply { trees.keys.mapTo(this, Version::id) }
                .joinToString(",")
        )
    }

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
                val isConstructor = lastMapping is MethodMappingView && lastMapping.isConstructor

                tree.allowedNamespaces[lastVersion]
                    ?.mapNotNullTo(mutableSetOf()) { ns ->
                        val name = if (isConstructor) "<init>" else (lastMapping.getDstName(ns) ?: return@mapNotNullTo null)
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
     * Tries to find a node with [keys] as mappings in the last mapped version.
     *
     * @param keys the mappings; [String] for classes and [NameDescriptorPair] for members (field, method); if multiple are specified, all of them must match
     * @return the node, null if not found
     */
    operator fun get(vararg keys: Any): Node<T>? {
        if (keys.isEmpty()) return null // return early, we're not searching for anything

        return if (keys.size == 1) {
            val key = keys[0]

            find { key in it.lastNames }
        } else {
            find { keys.all(it.lastNames::contains) }
        }
    }
}

/**
 * Sets an incremented ancestry node index for all nodes in-place.
 *
 * @param ns the namespace where the index should be stored
 * @param beginIndex the first node index
 */
fun AncestryTree<MappingTree.ElementMapping>.computeIndices(ns: String, beginIndex: Int = 0) {
    var index = beginIndex

    forEach { node ->
        node.setIndex(ns, index++)
    }
}

/**
 * Sets an ancestry node index for all elements of this node in-place.
 *
 * @param ns the namespace where the index should be stored
 * @param index the integer index of this node
 */
fun AncestryTree.Node<MappingTree.ElementMapping>.setIndex(ns: String, index: Int) {
    forEach { (_, elem) ->
        val nsId = elem.tree.getNamespaceId(ns)
        require(nsId < 0) {
            "Namespace $ns is not a destination namespace, missing or source"
        }

        elem.setDstName(index.toString(10), nsId)
    }
}
