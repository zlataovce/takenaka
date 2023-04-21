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
 * This class is thread-safe.
 *
 * @author Matouš Kučera
 */
abstract class AbstractMappingAnalyzer : MappingAnalyzer {
    // sort by kind deletion status -> delete elements first and then accept non-deletion resolutions
    override val problems = mutableListOf<Problem<*>>()

    /**
     * The problem write lock.
     */
    protected val lock = object {}

    @Synchronized
    override fun accept(tree: MappingTree) {
        tree.classes.forEach { klass ->
            acceptClass(klass)

            klass.fields.forEach(::acceptField)
            klass.methods.forEach(::acceptMethod)
        }
    }

    override fun acceptResolutions(kind: ProblemKind): List<Problem<*>> {
        synchronized(lock) {
            var acceptedResolutions = problems.filter { problem -> problem.kind == kind }

            val time = measureTimeMillis {
                // remove redundant resolutions, if we're going to remove the element anyway
                if (kind.deletesElement) {
                    val lastProblems = acceptedResolutions.associateByTo(IdentityHashMap(), keySelector = Problem<*>::element)
                    acceptedResolutions = lastProblems.values.toList()

                    problems.removeIf { problem -> problem.element in lastProblems.keys }
                } else {
                    problems.removeIf { problem -> problem.kind == kind }
                }

                acceptedResolutions.forEach(Problem<*>::acceptResolution)
            }

            logger.info { "accepted ${acceptedResolutions.size} $kind resolution(s) in ${time}ms" }

            return acceptedResolutions
        }
    }

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
     */
    protected abstract fun acceptClass(klass: MappingTree.ClassMapping)

    /**
     * Visits a field for analysis.
     */
    protected abstract fun acceptField(field: MappingTree.FieldMapping)

    /**
     * Visits a method for analysis.
     */
    protected abstract fun acceptMethod(method: MappingTree.MethodMapping)
}
