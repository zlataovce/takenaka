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

import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * A base for a mapping analyzer.
 *
 * @author Matouš Kučera
 */
interface MappingAnalyzer {
    /**
     * Index of problems by their kind.
     */
    val problems: MutableMap<ProblemKind, MutableList<Problem<*>>>

    /**
     * Reads a mapping tree and appends its problems to this analyzer.
     *
     * @param tree the mapping tree
     */
    fun accept(tree: MappingTree) {
        tree.classes.forEach { klass ->
            acceptClass(klass)

            klass.fields.forEach(::acceptField)
            klass.methods.forEach(::acceptMethod)
        }
    }

    /**
     * Accepts all resolutions of all problems.
     */
    fun acceptResolutions() {
        problems.forEach { (kind, _) -> acceptResolutions(kind) }
    }

    /**
     * Accepts all resolutions of problems with kind [kind].
     *
     * @param kind the problem kind
     * @return the accepted resolutions
     */
    fun acceptResolutions(kind: ProblemKind): List<Problem<*>> {
        val problemsWithKind = problems.remove(kind) ?: return emptyList()

        val time = measureTimeMillis {
            // remove all problems for an element, if there is at least one deletion resolution
            problemsWithKind
                .filter { problem -> problem.kind.resolvableByDeletion }
                .distinctBy(Problem<*>::element)
                .forEach { deletableProblem ->
                    problemsWithKind.removeIf { problem ->
                        deletableProblem !== problem && deletableProblem.element === problem.element
                    }
                }

            problemsWithKind.forEach(Problem<*>::acceptResolution)
        }
        logger.info { "accepted ${problemsWithKind.size} $kind resolution(s) in ${time}ms" }

        return problemsWithKind
    }

    fun <T : MappingTree.ElementMapping> addProblem(element: T, namespace: String?, kind: ProblemKind, resolution: ProblemResolution<T>) {
        problems.getOrPut(kind, ::mutableListOf) += Problem(element, namespace, kind, resolution)
    }

    fun acceptClass(klass: MappingTree.ClassMapping)
    fun acceptField(field: MappingTree.FieldMapping)
    fun acceptMethod(method: MappingTree.MethodMapping)
}
