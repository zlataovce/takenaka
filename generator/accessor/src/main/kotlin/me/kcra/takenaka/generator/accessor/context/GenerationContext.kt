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
import me.kcra.takenaka.generator.accessor.CodeLanguage
import me.kcra.takenaka.generator.accessor.context.impl.JavaGenerationContext
import me.kcra.takenaka.generator.accessor.context.impl.KotlinGenerationContext
import me.kcra.takenaka.generator.accessor.context.impl.TracingGenerationContext
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import java.io.PrintStream

/**
 * A base generation context.
 *
 * @author Matouš Kučera
 */
interface GenerationContext : CoroutineScope { // TODO: remove CoroutineScope
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
    fun generateClass(model: ClassAccessor, tree: ClassAncestryTree) {
    }

    /**
     * Generates a mapping lookup class with accessors that have been generated in this context.
     */
    fun generateLookupClass() {
    }

    /**
     * Generates extra resources needed for the function of the generated output.
     */
    fun generateExtras() {
    }
}

/**
 * Opens a generation context of the specified flavor.
 *
 * @param ancestryProvider the ancestry provider of the context
 * @param flavor the accessor flavor of the context
 * @return the context
 */
suspend inline fun AccessorGenerator.generationContext(ancestryProvider: AncestryProvider, flavor: CodeLanguage): GenerationContext = coroutineScope {
    when (flavor) {
        CodeLanguage.JAVA -> JavaGenerationContext(this@generationContext, ancestryProvider, this)
        CodeLanguage.KOTLIN -> KotlinGenerationContext(this@generationContext, ancestryProvider, this)
    }
}

/**
 * Opens a generation context of the specified flavor and operates on it.
 *
 * @param ancestryProvider the ancestry provider of the context
 * @param flavor the accessor flavor of the context
 * @param block the context user
 */
suspend inline fun <R> AccessorGenerator.generationContext(
    ancestryProvider: AncestryProvider,
    flavor: CodeLanguage,
    crossinline block: suspend GenerationContext.() -> R
): R {
    return block(generationContext(ancestryProvider, flavor))
}

/**
 * Opens a tracing generation context.
 *
 * @param out the tracing output stream
 * @param ancestryProvider the ancestry provider of the context
 * @return the context
 */
suspend inline fun AccessorGenerator.tracingContext(out: PrintStream, ancestryProvider: AncestryProvider): GenerationContext = coroutineScope {
    TracingGenerationContext(out, this@tracingContext, ancestryProvider, this)
}

/**
 * Opens a tracing generation context and operates on it.
 *
 * @param out the tracing output stream
 * @param ancestryProvider the ancestry provider of the context
 * @param block the context user
 */
suspend inline fun <R> AccessorGenerator.tracingContext(
    out: PrintStream,
    ancestryProvider: AncestryProvider,
    crossinline block: suspend GenerationContext.() -> R
): R {
    return block(tracingContext(out, ancestryProvider))
}
