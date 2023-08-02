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

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree

/**
 * Collects namespace IDs for all mapping trees that the ancestry tree is composed of.
 *
 * @param ns the namespace name
 * @param visitMissing whether the namespace should be appended to the trees, if it is missing
 * @return the namespace IDs, keyed by version
 */
fun <T : MappingTree, E : MappingTree.ElementMapping> AncestryTree<T, E>.collectNamespaceIds(ns: String, visitMissing: Boolean = true): Map<Version, Int> {
    return trees.entries
        .mapNotNull { (version, tree) ->
            var nsId = tree.getNamespaceId(ns)
            if (nsId == MappingTree.NULL_NAMESPACE_ID) {
                if (!visitMissing) return@mapNotNull null

                nsId = tree.maxNamespaceId

                check(tree is MappingVisitor) {
                    "Namespace $ns is not present in tree of version ${version.id} and tree does not implement MappingVisitor"
                }

                // add namespace at the end
                check(tree.visitHeader()) {
                    "Namespace $ns is not present in tree of version ${version.id} and tree declined header visit"
                }
                tree.visitNamespaces(tree.srcNamespace, tree.dstNamespaces + ns)
            }

            return@mapNotNull version to nsId
        }
        .toMap()
}

/**
 * Sets an incremented ancestry node index for all nodes in-place.
 *
 * @param ns the namespace where the index should be stored, created if not present
 * @param beginIndex the first node index, starts at 0
 * @param T the mapping tree type
 * @param E the mapping tree element type
 */
fun <T : MappingTree, E : MappingTree.ElementMapping> AncestryTree<T, E>.computeIndices(ns: String, beginIndex: Int = 0) {
    computeIndices(collectNamespaceIds(ns), beginIndex)
}

/**
 * Sets an incremented ancestry node index for all nodes in-place.
 *
 * @param namespaceIds the namespace IDs, keyed by version
 * @param beginIndex the first node index, starts at 0
 * @param T the mapping tree type
 * @param E the mapping tree element type
 */
fun <T : MappingTree, E : MappingTree.ElementMapping> AncestryTree<T, E>.computeIndices(namespaceIds: Map<Version, Int>, beginIndex: Int = 0) {
    var index = beginIndex

    forEach { node ->
        val nodeIndex = (index++).toString(10)

        node.forEach nodeEach@ { (version, elem) ->
            val nsId = namespaceIds[version] ?: return@nodeEach

            elem.setDstName(nodeIndex, nsId)
        }
    }
}
