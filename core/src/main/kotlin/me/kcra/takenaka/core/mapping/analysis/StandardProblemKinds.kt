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

package me.kcra.takenaka.core.mapping.analysis

/**
 * Basic mapping problems, analyzed by [MappingAnalyzerImpl].
 *
 * @property description the problem description
 * @property resolvableByDeletion whether the problem should be resolved by deleting the element
 * @author Matouš Kučera
 */
enum class StandardProblemKinds(override val description: String?, override val resolvableByDeletion: Boolean) : ProblemKind {
    NON_EXISTENT_MAPPING("mapping does not have modifiers visited or they are malformed, likely a client class", true),
    SYNTHETIC("mapping is synthetic, it does not exist at compile-time", true)
}
