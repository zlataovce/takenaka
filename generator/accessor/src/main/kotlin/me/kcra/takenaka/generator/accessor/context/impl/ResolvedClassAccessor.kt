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

import me.kcra.takenaka.core.mapping.ancestry.impl.ClassAncestryNode
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor

/**
 * A mapping of an accessor model to its overload index.
 */
typealias NameOverloads<T> = Map<T, Int>

/**
 * A class accessor with members resolved from their ancestry trees.
 *
 * @property model the class accessor model
 * @property node the class ancestry node
 * @property fields the field accessor models and ancestry nodes
 * @property constructors the constructor accessor models and ancestry nodes
 * @property methods the method accessor models and ancestry nodes
 * @property fieldOverloads the field accessor name overloads (fields can't be overloaded, but capitalization matters, which is a problem when making uppercase names from everything)
 * @property methodOverloads the method accessor name overloads
 */
data class ResolvedClassAccessor(
    val model: ClassAccessor,
    val node: ClassAncestryNode,
    val fields: List<ResolvedFieldPair>,
    val constructors: List<ResolvedConstructorPair>,
    val methods: List<ResolvedMethodPair>,
    val fieldOverloads: NameOverloads<FieldAccessor>,
    val methodOverloads: NameOverloads<MethodAccessor>
)
