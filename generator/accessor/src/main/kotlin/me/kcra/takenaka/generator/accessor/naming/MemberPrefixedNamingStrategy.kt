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

import me.kcra.takenaka.generator.accessor.util.camelToUpperSnakeCase

/**
 * A naming strategy which prefixes accessor fields representing class members
 * using `CONSTRUCTOR_`, `FIELD_` and `METHOD_` prefixes and suffix them using provided
 * index if it is not 0 (except for constructors where all indexes are suffixed).
 */
interface MemberPrefixedNamingStrategy: NamingStrategy {
    override fun constructor(index: Int): String = "CONSTRUCTOR_$index"

    override fun field(fieldName: String, index: Int, constantAccessor: Boolean): String =
        "FIELD_${fieldName.camelToUpperSnakeCase()}${index.let { if (it != 0) "_$it" else "" }}"

    override fun method(methodName: String, index: Int): String =
        "METHOD_${methodName.camelToUpperSnakeCase()}${index.let { if (it != 0) "_$it" else "" }}"
}