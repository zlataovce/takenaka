/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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

package me.kcra.takenaka.generator.accessor

import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ancestry.impl.ClassAncestryTree
import me.kcra.takenaka.generator.accessor.context.GenerationContext
import me.kcra.takenaka.generator.accessor.context.tracingContext
import me.kcra.takenaka.generator.accessor.util.isGlob
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import me.kcra.takenaka.generator.common.provider.MappingProvider
import java.io.PrintStream

/**
 * A generator that generates accessor debugging information.
 *
 * An instance can be reused, but it is **not** thread-safe!
 *
 * @property out the stream where output should be printed
 * @param workspace the workspace in which this generator can move around
 * @param config the accessor generation configuration
 * @author Matouš Kučera
 */
open class TracingAccessorGenerator(
    val out: PrintStream,
    workspace: Workspace,
    config: AccessorConfiguration
) : AccessorGenerator(workspace, config) {
    /**
     * Launches the generator with mappings provided by the provider.
     *
     * @param mappingProvider the mapping provider
     * @param ancestryProvider the ancestry provider
     */
    override suspend fun generate(mappingProvider: MappingProvider, ancestryProvider: AncestryProvider) {
        generateAccessors(
            tracingContext(out, ancestryProvider),
            ancestryProvider.klass(mappingProvider.get())
        )
    }

    /**
     * Generates the configured accessors in the supplied context.
     *
     * @param context the generation context
     * @param tree the ancestry tree
     */
    override suspend fun generateAccessors(context: GenerationContext, tree: ClassAncestryTree) { // no parallelization - deterministic
        // generate non-glob accessors before glob ones to ensure that an explicit accessor doesn't get ignored
        config.accessors.sortedBy { it.internalName.isGlob }
            .forEach { accessor -> context.generateClass(accessor, tree) }

        context.generateLookupClass()
        context.generateExtras()
    }
}
