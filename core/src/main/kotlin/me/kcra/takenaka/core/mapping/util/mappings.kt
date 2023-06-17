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
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.Opcodes
import java.lang.reflect.Modifier

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

/**
 * Formats a modifier integer into a string.
 *
 * @param mask the modifier mask (you can get that from the [Modifier] class)
 * @return the modifier string, **may end with a space**
 */
fun Int.formatModifiers(mask: Int = 0): String = buildString {
    val mMod = this@formatModifiers and mask

    if ((mMod and Opcodes.ACC_PUBLIC) != 0) append("public ")
    if ((mMod and Opcodes.ACC_PRIVATE) != 0) append("private ")
    if ((mMod and Opcodes.ACC_PROTECTED) != 0) append("protected ")
    if ((mMod and Opcodes.ACC_STATIC) != 0) append("static ")
    // enums can have an abstract modifier (methods included) if its constants have a custom impl
    // TODO: should we remove that?

    // an interface is implicitly abstract
    // we need to check the unmasked modifiers here, since ACC_INTERFACE is not among Modifier#classModifiers
    if ((mMod and Opcodes.ACC_ABSTRACT) != 0 && (this@formatModifiers and Opcodes.ACC_INTERFACE) == 0) append("abstract ")
    if ((mMod and Opcodes.ACC_FINAL) != 0) {
        // enums and records are implicitly final
        // we need to check the unmasked modifiers here, since ACC_ENUM is not among Modifier#classModifiers
        if (mask != Modifier.classModifiers() || ((this@formatModifiers and Opcodes.ACC_ENUM) == 0 && (this@formatModifiers and Opcodes.ACC_RECORD) == 0)) {
            append("final ")
        }
    }
    if ((mMod and Opcodes.ACC_NATIVE) != 0) append("native ")
    if ((mMod and Opcodes.ACC_STRICT) != 0) append("strict ")
    if ((mMod and Opcodes.ACC_SYNCHRONIZED) != 0) append("synchronized ")
    if ((mMod and Opcodes.ACC_TRANSIENT) != 0) append("transient ")
    if ((mMod and Opcodes.ACC_VOLATILE) != 0) append("volatile ")
}
