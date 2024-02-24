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

/**
 * A strategy for naming generated classes and its members.
 */
interface NamingStrategy {
    /**
     * Creates a new name for the specified class and the generated class type.
     *
     * @param className fully qualified name of the mapped class
     * @param classType type of the class being generated (Mapping, Accessor, ...).
     *          The naming strategy has to count with this information to not create conflicting names.
     * @return A partially qualified name of the resulting class to which a user-specified base-package will be prepended.
     */
    fun klass(className: String, classType: GeneratedClassType): String

    /**
     * Creates a new name for a field representing constructor accessor.
     *
     * @param index index of the constructor
     * @return name for a field representing the indexed constructor
     */
    fun constructor(index: Int): String

    /**
     * Creates a new name for a field representing field accessor.
     *
     * @param fieldName name of the requested field
     * @param index index of the requested field among fields with the same name (starts at 0)
     * @param constantAccessor whether a constant accessor (object supplier) is being generated for the field
     * @return name for a field representing the field
     */
    fun field(fieldName: String, index: Int, constantAccessor: Boolean): String

    /**
     * Modifies the accessor name for use as a MethodHandle field getter.
     *
     * @param fieldAccessor accessor field name resolved earlier by this naming strategy
     * @return a modified name for getter accessor
     */
    fun fieldGetter(fieldAccessor: String): String = fieldAccessor + "_GETTER"

    /**
     * Modifies the accessor name for use as a MethodHandle field setter.
     *
     * @param fieldAccessor accessor field name resolved earlier by this naming strategy
     * @return a modified name for setter accessor
     */
    fun fieldSetter(fieldAccessor: String): String = fieldAccessor + "_SETTER"

    /**
     * Creates a new name for a field representing method accessor.
     *
     * @param methodName name of the requested method
     * @param index index of the requested method among methods with the same name (starts at 0)
     * @return name for a field representing the method
     */
    fun method(methodName: String, index: Int): String
}