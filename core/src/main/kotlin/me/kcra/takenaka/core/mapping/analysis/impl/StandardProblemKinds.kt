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

package me.kcra.takenaka.core.mapping.analysis.impl

import me.kcra.takenaka.core.mapping.analysis.ProblemKind

/**
 * Basic mapping problems, analyzed by [MappingAnalyzerImpl].
 *
 * @property description the problem description
 * @property deletesElement whether the problem is resolved by deleting the element
 * @author Matouš Kučera
 */
enum class StandardProblemKinds(override val description: String?, override val deletesElement: Boolean) : ProblemKind {
    NON_EXISTENT_MAPPING("mapping does not have modifiers visited or they are malformed, likely a client class", true),
    SYNTHETIC("mapping is synthetic, it does not exist at compile-time", true),
    INNER_CLASS_OWNER_NOT_MAPPED("inner/anonymous class mapping does not have the owner name remapped", false),
    INHERITANCE_ERROR("method is overridden from a super type, but the mapping does not match", false),
    SPECIAL_METHOD_NOT_MAPPED("method does not have a mapping, but it can be guessed (constructor, static initializer or implicit enum method)", false)
}
