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

package me.kcra.takenaka.generator.accessor.naming

import me.kcra.takenaka.generator.accessor.GeneratedClassType
import me.kcra.takenaka.generator.accessor.GeneratedMemberType
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.model.ConstructorAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor

/**
 * A [NamingStrategy] implementation base that redirects calls to [next].
 *
 * @property next the strategy to delegate to
 * @author Matouš Kučera
 */
abstract class ForwardingNamingStrategy(private val next: NamingStrategy) : NamingStrategy {
    override fun klass(model: ClassAccessor, type: GeneratedClassType): String = next.klass(model, type)
    override fun field(model: FieldAccessor, index: Int): String = next.field(model, index)
    override fun fieldHandle(model: FieldAccessor, index: Int, mutating: Boolean): String = next.fieldHandle(model, index, mutating)
    override fun constant(model: FieldAccessor, index: Int): String = next.constant(model, index)
    override fun constructor(model: ConstructorAccessor, index: Int): String = next.constructor(model, index)
    override fun method(model: MethodAccessor, index: Int): String = next.method(model, index)
    override fun klass(type: GeneratedClassType): String = next.klass(type)
    override fun member(type: GeneratedMemberType): String = next.member(type)
}
