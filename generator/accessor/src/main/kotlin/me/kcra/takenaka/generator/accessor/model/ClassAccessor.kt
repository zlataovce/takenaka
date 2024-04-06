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

package me.kcra.takenaka.generator.accessor.model

import me.kcra.takenaka.core.mapping.toInternalName
import java.io.Serializable

/**
 * A class type accessor declaration.
 *
 * @property name the last (newest) mapped class name of the accessed class, may be a glob pattern
 * @property fields the accessed fields of the class
 * @property constructors the accessed constructors of the class
 * @property methods the accessed methods of the class
 * @property requiredTypes the types of class members that should be accessed in bulk
 * @author Matouš Kučera
 */
data class ClassAccessor(
    val name: String,
    val fields: List<FieldAccessor> = emptyList(),
    val constructors: List<ConstructorAccessor> = emptyList(),
    val methods: List<MethodAccessor> = emptyList(),
    val requiredTypes: RequiredMemberTypes = 0
) : Serializable {
    /**
     * Internalized variant of [name].
     */
    @Transient
    val internalName = name.toInternalName()

    /**
     * Creates a copy of this model, but with a different name.
     *
     * @param name the new model name
     */
    fun withName(name: String): ClassAccessor {
        return ClassAccessor(name, fields, constructors, methods, requiredTypes)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
