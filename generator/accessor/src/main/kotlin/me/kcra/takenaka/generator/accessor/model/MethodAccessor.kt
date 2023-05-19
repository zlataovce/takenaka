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

package me.kcra.takenaka.generator.accessor.model

import me.kcra.takenaka.core.Version
import java.io.Serializable

/**
 * A method accessor declaration.
 *
 * @property name the method name
 * @property type the method descriptor, may be incomplete (without a return type - inferred)
 * @property version the version of the declared reference name, null for last (newest)
 * @property chain disassociated mapping which should be merged with this one
 * @author Matouš Kučera
 */
data class MethodAccessor(
    val name: String,
    val type: String,
    val version: Version? = null,
    val chain: MethodAccessor? = null
) : Serializable {
    /**
     * Upper-case variant of [name].
     */
    @Transient
    val upperName = name.uppercase()

    /**
     * Whether the method descriptor ([type]) is incomplete, i.e. ends just before the return type is declared.
     */
    @Transient
    val isIncomplete = type.endsWith(')')

    companion object {
        private const val serialVersionUID = 1L
    }
}
