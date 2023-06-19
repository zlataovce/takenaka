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
import me.kcra.takenaka.core.util.md5Digest
import me.kcra.takenaka.core.util.updateAndHex
import net.fabricmc.mappingio.tree.MappingTree

/**
 * A hash of the ancestry tree, useful as a checksum.
 *
 * This is equal to a MD5 hash of the concatenated amount of nodes
 * and the mapped version IDs, e.g. `1234,1.19.4,1.19.3`.
 */
inline val AncestryTree<*>.hash: String
    get() = md5Digest.updateAndHex(
        mutableListOf(size.toString(10))
            .apply { trees.keys.mapTo(this, Version::id) }
            .joinToString(",")
    )

/**
 * Sets an incremented ancestry node index for all nodes in-place.
 *
 * @param ns the namespace where the index should be stored, created if not present
 * @param beginIndex the first node index, starts at 0
 */
fun AncestryTree<MappingTree.ElementMapping>.computeIndices(ns: String, beginIndex: Int = 0) {
    var index = beginIndex
    val namespaceIds = trees.entries.associate { (version, tree) ->
        tree as MappingTree // generic contract violated if this throws

        var nsId = tree.getNamespaceId(ns)
        if (nsId == MappingTree.NULL_NAMESPACE_ID) {
            nsId = tree.maxNamespaceId

            // add index namespace at the end
            tree.dstNamespaces = tree.dstNamespaces + ns
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
