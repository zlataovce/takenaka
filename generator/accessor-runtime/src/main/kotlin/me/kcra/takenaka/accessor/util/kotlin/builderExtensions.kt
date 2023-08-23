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

package me.kcra.takenaka.accessor.util.kotlin

import me.kcra.takenaka.accessor.mapping.ClassMapping
import me.kcra.takenaka.accessor.mapping.ConstructorMapping
import me.kcra.takenaka.accessor.mapping.FieldMapping
import me.kcra.takenaka.accessor.mapping.MappingLookup
import me.kcra.takenaka.accessor.mapping.MethodMapping

/**
 * Builds a new [MappingLookup].
 *
 * @param block the builder action
 * @return the [MappingLookup]
 */
inline fun mappingLookup(block: MappingLookup.() -> Unit): MappingLookup = MappingLookup().apply(block)

/**
 * Builds a new [ClassMapping].
 *
 * @param block the builder action
 * @return the [ClassMapping]
 */
inline fun classMapping(name: String, block: ClassMapping.() -> Unit): ClassMapping = ClassMapping(name).apply(block)

/**
 * Builds a new [FieldMapping] and appends it to the [ClassMapping].
 *
 * @param name the field name declared in the accessor model
 * @param block the builder action
 * @return the [FieldMapping]
 */
inline fun ClassMapping.field(name: String, block: FieldMapping.() -> Unit): FieldMapping = putField(name).apply(block)

/**
 * Builds a new [ConstructorMapping] and appends it to the [ClassMapping].
 *
 * @param block the builder action
 * @return the [ConstructorMapping]
 */
inline fun ClassMapping.constructor(block: ConstructorMapping.() -> Unit): ConstructorMapping = putConstructor().apply(block)

/**
 * Builds a new [MethodMapping] and appends it to the [ClassMapping].
 *
 * @param name the method name declared in the accessor model
 * @param block the builder action
 * @return the [MethodMapping]
 */
inline fun ClassMapping.method(name: String, block: MethodMapping.() -> Unit): MethodMapping = putMethod(name).apply(block)
