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

package me.kcra.takenaka.core.mapping

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.util.hexValue
import me.kcra.takenaka.core.util.md5Digest
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * A collection of version keyed joined mapping trees.
 */
typealias MappingsMap = Map<Version, MappingTreeView>

/**
 * A collection of version keyed joined mutable mapping trees.
 */
typealias MutableMappingsMap = Map<Version, MappingTree>

/**
 * Returns IDs of all namespaces in this tree.
 */
inline val MappingTreeView.allNamespaceIds: IntRange
    get() = MappingTreeView.SRC_NAMESPACE_ID until maxNamespaceId

/**
 * Returns IDs of all destination namespaces in this tree (excludes the obfuscated namespace).
 */
inline val MappingTreeView.dstNamespaceIds: IntRange
    get() = 0 until maxNamespaceId

/**
 * Returns whether the tree has a namespace with the specified name.
 *
 * @param ns the namespace name
 * @return does the namespace exist in the tree?
 */
fun MappingTreeView.hasNamespace(ns: String): Boolean = getNamespaceId(ns) != MappingTreeView.NULL_NAMESPACE_ID

/**
 * Computes a hash of all destination mappings of this element.
 *
 * The resulting hash is stable, meaning the order of namespaces won't affect it.
 */
val MappingTreeView.ElementMappingView.hash: String
    get() = md5Digest
        .apply {
            update(
                tree.dstNamespaceIds
                    .mapNotNull { getDstName(it) }
                    .sorted()
                    .joinToString(",")
                    .encodeToByteArray()
            )
        }
        .hexValue
