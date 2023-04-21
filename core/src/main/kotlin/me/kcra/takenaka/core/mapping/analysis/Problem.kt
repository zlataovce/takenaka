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

import net.fabricmc.mappingio.tree.MappingTree

/**
 * A function for resolving a problem.
 */
typealias ProblemResolution<T> = Problem<T>.() -> Unit

/**
 * An inconsistency/error in mappings.
 *
 * @property element the related mapping element
 * @property namespace the name of the namespace where this error occurred, null if it affects the element's nature
 * @property kind the kind of the problem
 * @property resolution a function for resolving this problem
 */
class Problem<T : MappingTree.ElementMapping>(val element: T, val namespace: String?, val kind: ProblemKind, val resolution: ProblemResolution<T>) {
    /**
     * Resolves this problem with [resolution].
     */
    fun acceptResolution() = resolution()
}
