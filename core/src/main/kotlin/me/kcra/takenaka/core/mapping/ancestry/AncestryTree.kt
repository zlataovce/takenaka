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
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * A mapping ancestry tree.
 *
 * @property allowedNamespaces namespace IDs that are used in this tree for tracing history, per version
 * @author Matouš Kučera
 */
data class AncestryTree<T : MappingTree.ElementMapping, K>(val allowedNamespaces: MutableMap<Version, List<Int>> = mutableMapOf()) : MutableList<AncestryTree<T, K>.Node> by mutableListOf() {
    /**
     * A node in the ancestry tree.
     * This represents one element (class, field, method) in multiple versions.
     *
     * @property keys a set of multiple mappings from multiple versions, this is used as a unique identifier for the member
     * @property mappings the class mappings, keyed by the version
     */
    inner class Node(
        val keys: MutableSet<K> = mutableSetOf(),
        val mappings: MutableMap<Version, T> = mutableMapOf()
    ) {
        /**
         * The parent tree's allowed namespaces.
         */
        val allowedNamespaces by this@AncestryTree::allowedNamespaces
    }

    /**
     * Tries to find a node in the tree.
     * This works by checking if the supplied keys and the node's keys are disjoint, if not, it's the mapping we want.
     *
     * @param keys the keys
     * @return the node, null if not found
     */
    operator fun get(keys: List<K>): Node? = find { it.keys.any { k -> k in keys } }
}

typealias ElementKey = String
typealias MemberKey = Pair<String, String>

val Pair<Any?, Any?>.valid
    get() = first != null && second != null

/**
 * Collects allowed namespace IDs from a mapping tree.
 *
 * @param allowedNamespaces the allowed namespaces, all namespaces are considered if empty
 * @return the namespace IDs
 */
fun MappingTreeView.collectAllowedNamespaces(allowedNamespaces: List<String>): List<Int> {
    var treeAllowedNamespaces = allowedNamespaces
        .map(::getNamespaceId)
        .filter { it != MappingTree.NULL_NAMESPACE_ID }

    if (treeAllowedNamespaces.isEmpty()) {
        treeAllowedNamespaces = (0 until maxNamespaceId).toList()
    }

    return treeAllowedNamespaces
}
