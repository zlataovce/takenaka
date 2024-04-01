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
import java.io.Serializable

/**
 * A strategy for naming generated classes and its members.
 *
 * @author Michal Turek
 * @author Matouš Kučera
 */
interface NamingStrategy : Serializable {
    /**
     * Creates a new class name for the class type in the context of the specified class model.
     *
     * @param model the accessor model
     * @param type the type of the class being generated
     * @return the class name
     */
    fun klass(model: ClassAccessor, type: GeneratedClassType): String

    /**
     * Creates a new field name for the specified field model.
     *
     * @param model the accessor model
     * @param index the overload index of the model declaration
     * @return the field name
     */
    fun field(model: FieldAccessor, index: Int): String

    /**
     * Creates a new field name for the specified field model ([java.lang.invoke.MethodHandle] variant).
     *
     * @param model the accessor model
     * @param index the overload index of the model declaration
     * @param mutating whether a setter handle name should be generated
     * @return the field name
     */
    fun fieldHandle(model: FieldAccessor, index: Int, mutating: Boolean): String

    /**
     * Creates a new field name for the specified constant field model.
     *
     * @param model the accessor model
     * @param index the overload index of the model declaration
     * @return the field name
     */
    fun constant(model: FieldAccessor, index: Int): String

    /**
     * Creates a new field name for the specified constructor model.
     *
     * @param model the accessor model
     * @param index the index of the model declaration
     * @return the field name
     */
    fun constructor(model: ConstructorAccessor, index: Int): String

    /**
     * Creates a new field name for the specified method model.
     *
     * @param model the accessor model
     * @param index the overload index of the model declaration
     * @return the field name
     */
    fun method(model: MethodAccessor, index: Int): String

    // context-free cases

    /**
     * Creates a new class name for the specified type.
     *
     * @param type the type of the class being generated
     * @return the class name
     */
    fun klass(type: GeneratedClassType): String

    /**
     * Creates a new member name for the specified type.
     *
     * @param type the type of the member being generated
     * @return the member name
     */
    fun member(type: GeneratedMemberType): String
}
