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

package me.kcra.takenaka.generator.accessor.context.impl

import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.outputStream

/**
 * A generation context that prints information about resolved accessors.
 *
 * @property output the stream where output should be printed
 * @param generator the generator
 * @param ancestryProvider the ancestry provider
 * @param contextScope the coroutine scope of this context
 * @author Matouš Kučera
 */
open class TracingGenerationContext(
    val output: PrintStream,
    generator: AccessorGenerator,
    ancestryProvider: AncestryProvider,
    contextScope: CoroutineScope
) : AbstractGenerationContext(generator, ancestryProvider, contextScope) {
    /**
     * Constructs a new [TracingGenerationContext] with an [OutputStream].
     *
     * @param out the stream where output should be printed
     * @param generator the generator
     * @param ancestryProvider the ancestry provider
     * @param contextScope the coroutine scope of this context
     */
    constructor(out: OutputStream, generator: AccessorGenerator, ancestryProvider: AncestryProvider, contextScope: CoroutineScope)
            : this(PrintStream(out), generator, ancestryProvider, contextScope)

    /**
     * Constructs a new [TracingGenerationContext] with a file.
     *
     * @param out the file where output should be written
     * @param generator the generator
     * @param ancestryProvider the ancestry provider
     * @param contextScope the coroutine scope of this context
     */
    constructor(out: Path, generator: AccessorGenerator, ancestryProvider: AncestryProvider, contextScope: CoroutineScope)
            : this(PrintStream(out.outputStream()), generator, ancestryProvider, contextScope)

    /**
     * Constructs a new [TracingGenerationContext] that writes to standard output.
     *
     * @param generator the generator
     * @param ancestryProvider the ancestry provider
     * @param contextScope the coroutine scope of this context
     */
    constructor(generator: AccessorGenerator, ancestryProvider: AncestryProvider, contextScope: CoroutineScope)
            : this(System.out, generator, ancestryProvider, contextScope)

    /**
     * Writes an accessor report to the stream.
     *
     * @param resolvedAccessor the accessor model
     */
    @Synchronized
    override fun generateClass(resolvedAccessor: ResolvedClassAccessor) {
        TODO("Not implemented yet")
    }
}
