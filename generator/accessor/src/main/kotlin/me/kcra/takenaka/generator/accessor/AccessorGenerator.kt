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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ancestry.impl.ClassAncestryTree
import me.kcra.takenaka.generator.accessor.context.GenerationContext
import me.kcra.takenaka.generator.accessor.context.generationContext
import me.kcra.takenaka.generator.accessor.util.isGlob
import me.kcra.takenaka.generator.common.Generator
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import me.kcra.takenaka.generator.common.provider.MappingProvider

/**
 * A generator that generates source code for accessing obfuscated elements using mapped names across versions.
 *
 * Generated code depends on the `generator-accessor-runtime` module.
 *
 * An instance can be reused, but it is **not** thread-safe!
 *
 * @property workspace the workspace in which this generator can move around
 * @property config the accessor generation configuration
 * @author Matouš Kučera
 */
open class AccessorGenerator(override val workspace: Workspace, val config: AccessorConfiguration) : Generator {
    /**
     * Launches the generator with mappings provided by the provider.
     *
     * @param mappingProvider the mapping provider
     * @param ancestryProvider the ancestry provider
     */
    override suspend fun generate(mappingProvider: MappingProvider, ancestryProvider: AncestryProvider) {
        generateAccessors(
            generationContext(ancestryProvider, config.codeLanguage),
            ancestryProvider.klass(mappingProvider.get())
        )
    }

    /**
     * Generates the configured accessors in the supplied context.
     *
     * @param context the generation context
     * @param tree the ancestry tree
     */
    protected open suspend fun generateAccessors(context: GenerationContext, tree: ClassAncestryTree) {
        // generate non-glob accessors before glob ones to ensure that an explicit accessor doesn't get ignored
        config.accessors.partition { !it.internalName.isGlob }.toList()
            .forEach { group ->
                coroutineScope {
                    group.forEach { accessor ->
                        launch(Dispatchers.Default + CoroutineName("generate-coro")) {
                            context.generateClass(accessor, tree)
                        }
                    }
                }
            }

        context.generateLookupClass()
        context.generateExtras()
    }
}
