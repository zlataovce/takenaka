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
import me.kcra.takenaka.core.mapping.ancestry.impl.ClassAncestryTree
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.context.impl.JavaGenerationContext
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.common.provider.AncestryProvider

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
     * @param tree the class ancestry tree
     */
    fun generateClass(model: ClassAccessor, tree: ClassAncestryTree)

    /**
     * Generates a mapping pool class with accessors
     * that have been generated in this context.
     */
    fun generatePool()
}

/**
 * Opens a generation context of the specified flavor.
 *
 * @param ancestryProvider the ancestry provider of the context
 * @param flavor the accessor flavor of the context
 * @param block the context user
 */
suspend inline fun <R> AccessorGenerator.generationContext(
    ancestryProvider: AncestryProvider,
    flavor: LanguageFlavor,
    crossinline block: suspend GenerationContext.() -> R
): R = coroutineScope {
    block(
        when (flavor) {
            LanguageFlavor.JAVA -> JavaGenerationContext(this@generationContext, ancestryProvider, this)
            else -> throw UnsupportedOperationException("Flavor $flavor not supported")
        }
    )
}
