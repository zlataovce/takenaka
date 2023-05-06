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
import me.kcra.takenaka.core.mapping.ancestry.ClassAncestryNode
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.model.ClassAccessor

/**
 * A generation context that emits Java code.
 *
 * @property generator the generator
 * @param contextScope the coroutine scope of this context
 * @author Matouš Kučera
 */
open class JavaGenerationContext(
    override val generator: AccessorGenerator,
    contextScope: CoroutineScope
) : GenerationContext, CoroutineScope by contextScope {
    /**
     * Generates an accessor class from a model in Java.
     *
     * @param model the accessor model
     * @param node the ancestry node of the class defined by the model
     */
    override fun generateClass(model: ClassAccessor, node: ClassAncestryNode) {
        TODO("Not yet implemented")
    }
}
