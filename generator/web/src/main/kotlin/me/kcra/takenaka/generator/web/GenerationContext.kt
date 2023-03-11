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

package me.kcra.takenaka.generator.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import java.lang.reflect.Modifier

/**
 * A generation context.
 *
 * @author Matouš Kučera
 */
class GenerationContext(coroutineScope: CoroutineScope, val generator: WebGenerator, val styleProvider: StyleProvider) : CoroutineScope by coroutineScope {
    val index: ClassSearchIndex by generator::index

    /**
     * Gets a "friendly" destination name of an element.
     *
     * @param elem the element
     * @return the name
     */
    fun getFriendlyDstName(elem: MappingTree.ElementMapping): String {
        generator.namespaceFriendlinessIndex.forEach { ns ->
            elem.getName(ns)?.let { return it }
        }
        return (0 until elem.tree.maxNamespaceId).firstNotNullOfOrNull(elem::getDstName) ?: elem.srcName
    }

    /**
     * Gets a CSS color of the supplied namespace.
     *
     * @param ns the namespace
     * @return the color
     */
    fun getNamespaceFriendlyName(ns: String): String? = generator.namespaces[ns]?.friendlyName

    /**
     * Gets a CSS color of the supplied namespace.
     *
     * @param ns the namespace
     * @return the color
     */
    fun getNamespaceBadgeColor(ns: String): String = generator.namespaces[ns]?.color ?: "#94a3b8"

    /**
     * Formats a modifier integer into a string.
     *
     * @param mod the modifier integer
     * @param mask the modifier mask (you can get that from the [Modifier] class or use 0)
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
        // an enum is implicitly final
        // we need to check the unmasked modifiers here, since ACC_ENUM is not among Modifier#classModifiers
        if ((mMod and Opcodes.ACC_FINAL) != 0 && (mask != Modifier.classModifiers() || (mod and Opcodes.ACC_ENUM) == 0)) append("final ")
        if ((mMod and Opcodes.ACC_NATIVE) != 0) append("native ")
        if ((mMod and Opcodes.ACC_STRICT) != 0) append("strict ")
        if ((mMod and Opcodes.ACC_SYNCHRONIZED) != 0) append("synchronized ")
        if ((mMod and Opcodes.ACC_TRANSIENT) != 0) append("transient ")
        if ((mMod and Opcodes.ACC_VOLATILE) != 0) append("volatile ")
    }
}

/**
 * Opens a generation context.
 *
 * @param styleProvider the style provider that will be used in the context
 * @param block the context user
 */
inline fun <R> WebGenerator.generationContext(noinline styleProvider: StyleProvider, crossinline block: suspend GenerationContext.() -> R): R =
    runBlocking(coroutineDispatcher) { block(GenerationContext(this, this@generationContext, styleProvider)) }
