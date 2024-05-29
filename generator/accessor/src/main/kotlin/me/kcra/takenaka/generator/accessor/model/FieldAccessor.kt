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
import me.kcra.takenaka.generator.accessor.util.camelToUpperSnakeCase
import java.io.Serializable

/**
 * A field accessor declaration.
 *
 * @property name the mapped name of the field
 * @property type the field descriptor, null if it should be inferred
 * @property version the version of the declared reference name, null for last (newest)
 * @property chain disassociated mapping which should be merged with this one
 * @author Matouš Kučera
 */
data class FieldAccessor(
    val name: String,
    val type: String? = null,
    val version: String? = null,
    val chain: FieldAccessor? = null
) : Serializable {
    /**
     * Upper-case variant of [name].
     */
    @Transient
    val upperName = name.camelToUpperSnakeCase()

    /**
     * Internalized variant of [type].
     */
    @Transient
    val internalType = type?.toInternalName()

    companion object {
        private const val serialVersionUID = 1L
    }
}
