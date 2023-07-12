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

package me.kcra.takenaka.core.mapping.ancestry.impl

import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree

/**
 * Sets an incremented ancestry node index for all nodes in-place.
 *
 * @param ns the namespace where the index should be stored, created if not present
 * @param beginIndex the first node index, starts at 0
 * @param T the mapping tree type
 * @param E the mapping tree element type
 */
fun <T : MappingTree, E : MappingTree.ElementMapping> AncestryTree<T, E>.computeIndices(ns: String, beginIndex: Int = 0) {
    var index = beginIndex
    val namespaceIds = trees.entries.associate { (version, tree) ->
        var nsId = tree.getNamespaceId(ns)
        if (nsId == MappingTree.NULL_NAMESPACE_ID) {
            nsId = tree.maxNamespaceId

            check(tree is MappingVisitor) {
                "Namespace $ns is not present in tree of version ${version.id} and tree does not implement MappingVisitor"
            }
            // add index namespace at the end
            check(tree.visitHeader()) {
                "Namespace $ns is not present in tree of version ${version.id} and tree declined header visit"
            }
            tree.visitNamespaces(tree.srcNamespace, tree.dstNamespaces + ns)
        }

        require(nsId != MappingTree.SRC_NAMESPACE_ID) {
            "Namespace $ns in version ${version.id} is a source namespace"
        }

        version to nsId
    }

    forEach { node ->
        val nodeIndex = (index++).toString(10)

        node.forEach { (version, elem) ->
            val nsId = namespaceIds[version]
                ?: error("Version ${version.id} is present in a node, but wasn't in tree summary") // generic contract violated if this throws

            elem.setDstName(nodeIndex, nsId)
        }
    }
}
