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

package me.kcra.takenaka.generator.accessor.context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import me.kcra.takenaka.core.mapping.ancestry.impl.ClassAncestryNode
import me.kcra.takenaka.core.mapping.util.dstNamespaceIds
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import net.fabricmc.mappingio.tree.MappingTreeView.MemberMappingView
import org.objectweb.asm.Type

/**
 * A base generation context.
 *
 * @author Matouš Kučera
 */
interface GenerationContext : CoroutineScope {
    /**
     * The generator.
     */
    val generator: AccessorGenerator

    /**
     * Generates an accessor class from a model.
     *
     * @param model the accessor model
     * @param node the ancestry node of the class defined by the model
     */
    fun generateClass(model: ClassAccessor, node: ClassAncestryNode)

    /**
     * Returns a parsed [Type] of a member descriptor picked based on the friendliness index.
     *
     * @param member the member
     * @return the [Type]
     */
    fun getFriendlyType(member: MemberMappingView): Type {
        generator.config.namespaceFriendlinessIndex.forEach { ns ->
            member.getDesc(ns)?.let { return Type.getType(it) }
        }
        return Type.getType(member.tree.dstNamespaceIds.firstNotNullOfOrNull(member::getDstDesc) ?: member.srcDesc)
    }
}

/**
 * Opens a generation context of the specified flavor.
 *
 * @param flavor the accessor flavor of the context
 * @param block the context user
 */
suspend inline fun <R> AccessorGenerator.generationContext(flavor: LanguageFlavor, crossinline block: suspend GenerationContext.() -> R): R =
    coroutineScope {
        block(
            when (flavor) {
                LanguageFlavor.JAVA -> JavaGenerationContext(this@generationContext, this)
                else -> throw UnsupportedOperationException("Flavor $flavor not supported")
            }
        )
    }
