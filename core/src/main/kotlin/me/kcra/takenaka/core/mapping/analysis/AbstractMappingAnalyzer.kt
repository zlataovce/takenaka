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
    override val problems = mutableListOf<Problem<*>>()

    /**
     * A write lock for [problems].
     */
    protected val lock = object {}

    /**
     * Reads a mapping tree and appends its problems to this analyzer.
     *
     * Implementations should read a tree in order to allow for contextual information to be stored temporarily:
     * `read a class -> read fields of that class -> read methods of that class`
     *
     * @param tree the mapping tree
     */
    @Synchronized
    override fun accept(tree: MappingTree) {
        tree.classes.forEach { klass ->
            acceptClass(klass)

            klass.fields.forEach(::acceptField)
            klass.methods.forEach(::acceptMethod)
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
        synchronized(lock) {
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
        synchronized(lock) {
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
    protected fun <T : MappingTree.ElementMapping> addProblem(element: T, namespace: String?, kind: ProblemKind, resolution: ProblemResolution<T>) {
        synchronized(lock) {
            problems += Problem(element, namespace, kind, resolution)
        }
    }

    /**
     * Visits a class for analysis.
     *
     * It is guaranteed that this method will be called only by one thread at a time,
     * therefore the implementation can store information in the class context temporarily.
     */
    protected abstract fun acceptClass(klass: MappingTree.ClassMapping)

    /**
     * Visits a field for analysis.
     *
     * It is guaranteed that this method will be called only by one thread at a time,
     * therefore the implementation can store information in the class context temporarily.
     */
    protected abstract fun acceptField(field: MappingTree.FieldMapping)

    /**
     * Visits a method for analysis.
     *
     * It is guaranteed that this method will be called only by one thread at a time,
     * therefore the implementation can store information in the class context temporarily.
     */
    protected abstract fun acceptMethod(method: MappingTree.MethodMapping)
}
