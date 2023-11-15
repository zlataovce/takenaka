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

package me.kcra.takenaka.core.mapping.util

import me.kcra.takenaka.core.util.md5Digest
import me.kcra.takenaka.core.util.updateAndHex
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

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
operator fun MappingTreeView.contains(ns: String): Boolean = getNamespaceId(ns) != MappingTreeView.NULL_NAMESPACE_ID

/**
 * Returns a single metadata value under [key], null if not found or value was a null literal.
 *
 * @param key the metadata key
 * @return the metadata value
 */
fun MappingTreeView.getSingleMetadata(key: String): String? = getMetadata(key)?.firstOrNull()?.value

/**
 * Computes a hash of all destination mappings of this element.
 *
 * The resulting hash is stable, meaning the order of namespaces won't affect it.
 */
val MappingTreeView.ElementMappingView.hash: String
    get() = md5Digest.updateAndHex(
        tree.dstNamespaceIds
            .mapNotNull(::getDstName)
            .sorted()
            .joinToString(",")
    )

// HACK!
val NEXT_VISITOR_FIELD: MethodHandle = MethodHandles.lookup()
    .unreflectGetter(ForwardingMappingVisitor::class.java.getDeclaredField("next").apply { isAccessible = true })

/**
 * Gets the delegate visitor.
 */
inline val ForwardingMappingVisitor.next: MappingVisitor
    get() = NEXT_VISITOR_FIELD.invokeExact(this) as MappingVisitor

/**
 * Unwraps a [ForwardingMappingVisitor] forward chain to the initial visitor.
 *
 * @return the initial visitor
 */
tailrec fun MappingVisitor.unwrap(): MappingVisitor {
    if (this is ForwardingMappingVisitor) {
        return next.unwrap()
    }

    return this
}
