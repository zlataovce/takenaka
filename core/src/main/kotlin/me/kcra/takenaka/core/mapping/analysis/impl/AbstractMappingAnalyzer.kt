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

package me.kcra.takenaka.core.mapping.analysis.impl

import me.kcra.takenaka.core.mapping.analysis.MappingAnalyzer
import me.kcra.takenaka.core.mapping.analysis.Problem
import me.kcra.takenaka.core.mapping.analysis.ProblemKind
import me.kcra.takenaka.core.mapping.analysis.ProblemResolution
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree
import java.util.*
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * An abstract base for a [MappingAnalyzer] implementation.
 *
 * This class is thread-safe, but subclasses may not be.
 *
 * @author Matouš Kučera
 */
abstract class AbstractMappingAnalyzer : MappingAnalyzer {
    /**
     * The currently discovered problems.
     */
    override val problems: MutableList<Problem<*>> = Collections.synchronizedList(mutableListOf<Problem<*>>())

    /**
     * Reads a mapping tree and appends its problems to this analyzer.
     *
     * @param tree the mapping tree
     */
    override fun accept(tree: MappingTree) {
        tree.classes.forEach { klass ->
            val klassCtx = acceptClass(klass)

            klass.fields.forEach(klassCtx::acceptField)
            klass.methods.forEach(klassCtx::acceptMethod)
        }
    }

    /**
     * Accepts resolutions of all problems with a specific problem kind.
     *
     * If the problem kind is marked as deletable ([ProblemKind.deletesElement]),
     * resolutions *of any kind*, that no longer have a target element, are removed from [problems]
     * and only the first deletable resolution is accepted.
     *
     * @param kind the problem kind
     * @return the accepted resolutions
     */
    override fun acceptResolutions(kind: ProblemKind): List<Problem<*>> {
        synchronized(problems) {
            var acceptedResolutions = problems.filter { problem -> problem.kind == kind }

            val time = measureTimeMillis {
                if (kind.deletesElement) {
                    // remove redundant resolutions, if we're going to remove the element anyway
                    val lastProblems = acceptedResolutions.associateByTo(IdentityHashMap(), keySelector = Problem<*>::element)
                    acceptedResolutions = lastProblems.values.toList()

                    problems.removeIf { problem -> problem.element in lastProblems.keys }

                    // perf: accept deletion in batch manually
                    acceptedResolutions
                        .groupBy(keySelector = Problem<*>::parentCollection, valueTransform = Problem<*>::element)
                        .forEach { (parentCollection, elements) ->
                            parentCollection.removeIf(elements::contains)
                        }
                } else {
                    problems.removeIf { problem -> problem.kind == kind }

                    acceptedResolutions.forEach(Problem<*>::acceptResolution)
                }
            }

            logger.info { "accepted ${acceptedResolutions.size} $kind resolution(s) in ${time}ms" }

            return acceptedResolutions
        }
    }

    /**
     * Accepts resolutions of all problems of an element with a specific problem kind.
     *
     * If the problem kind is marked as deletable ([ProblemKind.deletesElement]),
     * resolutions *of any kind*, that are bound to [element], are removed from [problems]
     * and only the first deletable resolution is accepted.
     *
     * @param element the troubled element
     * @param kind the problem kind
     * @return the accepted resolutions
     */
    override fun <T : MappingTree.ElementMapping> acceptElementResolutions(
        element: T,
        kind: ProblemKind
    ): List<Problem<T>> {
        synchronized(problems) {
            @Suppress("UNCHECKED_CAST")
            var acceptedResolutions = problems.filter { problem -> problem.kind == kind && problem.element === element } as List<Problem<T>>

            // remove redundant resolutions, if we're going to remove the element anyway
            if (kind.deletesElement) {
                val deletableProblem = acceptedResolutions.firstOrNull() ?: return emptyList()
                acceptedResolutions = listOf(deletableProblem)

                problems.removeIf { problem ->
                    problem.element === element && problem !== deletableProblem
                }
            } else {
                problems.removeIf { problem -> problem.kind == kind && problem.element === element }
            }

            acceptedResolutions.forEach(Problem<*>::acceptResolution)

            return acceptedResolutions
        }
    }

    /**
     * Adds a new problem for an element.
     *
     * @see Problem
     */
    protected fun <T : MappingTree.ElementMapping> problem(element: T, namespace: String?, kind: ProblemKind, resolution: ProblemResolution<T>) {
        problems += Problem(element, namespace, kind, resolution)
    }

    /**
     * Visits a class for analysis.
     */
    protected abstract fun acceptClass(klass: MappingTree.ClassMapping): ClassAnalysisContext
}

