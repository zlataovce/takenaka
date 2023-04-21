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
 * A mapping semantic analyzer, useful for identifying and correcting mapping errors in a selective manner.
 *
 * @author Matouš Kučera
 */
interface MappingAnalyzer {
    /**
     * Problems currently discovered by this analyzer.
     */
    val problems: List<Problem<*>>

    /**
     * Kinds of problems currently discovered by this analyzer.
     */
    val problemKinds: List<ProblemKind>
        get() = problems.mapTo(mutableSetOf(), transform = Problem<*>::kind).sortedByDescending(ProblemKind::deletesElement)

    /**
     * Reads a mapping tree and appends its problems to this analyzer.
     *
     * @param tree the mapping tree
     */
    fun accept(tree: MappingTree)

    /**
     * Accepts resolutions of all problems.
     */
    fun acceptResolutions() {
        problemKinds.forEach(::acceptResolutions)
    }

    /**
     * Accepts resolutions of all problems with a specific problem kind.
     *
     * @param kind the problem kind
     * @return the accepted resolutions
     */
    fun acceptResolutions(kind: ProblemKind): List<Problem<*>>

    /**
     * Accepts resolutions of all problems of an element.
     *
     * @param element the troubled element
     * @return the accepted resolutions
     */
    fun <T : MappingTree.ElementMapping> acceptElementResolutions(element: T): List<Problem<T>> =
        problemKinds.flatMap { kind -> acceptElementResolutions(element, kind) }

    /**
     * Accepts resolutions of all problems of an element with a specific problem kind.
     *
     * @param element the troubled element
     * @param kind the problem kind
     * @return the accepted resolutions
     */
    fun <T : MappingTree.ElementMapping> acceptElementResolutions(element: T, kind: ProblemKind): List<Problem<T>>
}
