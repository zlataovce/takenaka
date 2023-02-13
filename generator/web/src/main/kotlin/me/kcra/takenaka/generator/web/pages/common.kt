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

package me.kcra.takenaka.generator.web.pages

import org.objectweb.asm.Opcodes

/**
 * Formats a modifier integer into a string.
 *
 * @param mod the modifier integer
 * @param mask the modifier mask (you can get that from the [java.lang.reflect.Modifier] class or use 0)
 * @return the modifier string
 */
fun formatModifiers(mod: Int, mask: Int): String = buildString {
    val mMod = mod and mask

    if ((mMod and Opcodes.ACC_PUBLIC) != 0) append("public ")
    if ((mMod and Opcodes.ACC_PRIVATE) != 0) append("private ")
    if ((mMod and Opcodes.ACC_PROTECTED) != 0) append("protected ")
    if ((mMod and Opcodes.ACC_STATIC) != 0) append("static ")
    // an interface is implicitly abstract
    // we need to check the unmasked modifiers here, since ACC_INTERFACE is not among Modifier#classModifiers
    if ((mMod and Opcodes.ACC_ABSTRACT) != 0 && (mod and Opcodes.ACC_INTERFACE) == 0) append("abstract ")
    if ((mMod and Opcodes.ACC_FINAL) != 0) append("final ")
    if ((mMod and Opcodes.ACC_NATIVE) != 0) append("native ")
    if ((mMod and Opcodes.ACC_STRICT) != 0) append("strict ")
    if ((mMod and Opcodes.ACC_SYNCHRONIZED) != 0) append("synchronized ")
    if ((mMod and Opcodes.ACC_TRANSIENT) != 0) append("transient ")
    if ((mMod and Opcodes.ACC_VOLATILE) != 0) append("volatile ")
}

/**
 * Replaces dots with slashes (e.g. qualified class name to internal name).
 *
 * @return the replaced string
 */
fun String.toInternalName(): String = replace('.', '/')

/**
 * Replaces slashes with dots (e.g. internal name to qualified class name).
 *
 * @return the replaced string
 */
fun String.fromInternalName(): String = replace('/', '.')
