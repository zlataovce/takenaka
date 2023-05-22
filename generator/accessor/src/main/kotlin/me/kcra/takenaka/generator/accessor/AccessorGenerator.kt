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

package me.kcra.takenaka.generator.accessor

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.MutableMappingsMap
import me.kcra.takenaka.core.mapping.ancestry.impl.classAncestryTreeOf
import me.kcra.takenaka.generator.accessor.context.generationContext
import me.kcra.takenaka.generator.common.Generator
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

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
class AccessorGenerator(override val workspace: Workspace, val config: AccessorConfiguration) : Generator {
    /**
     * Launches the generator with a pre-determined set of mappings.
     *
     * @param mappings the mappings
     */
    override suspend fun generate(mappings: MutableMappingsMap) {
        val tree = classAncestryTreeOf(mappings, config.historicalNamespaces)

        generationContext(config.languageFlavor) {
            config.accessors.forEach { classAccessor ->
                launch(Dispatchers.Default + CoroutineName("generate-coro")) {
                    logger.info { "generating accessors for class '${classAccessor.internalName}'" }

                    val node = checkNotNull(tree[classAccessor.internalName]) {
                        "Class ancestry node with name ${classAccessor.internalName} not found"
                    }

                    generateClass(classAccessor, node)
                }
            }
        }
    }
}
