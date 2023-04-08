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

package me.kcra.takenaka.core.mapping.adapter

import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.mapping.resolve.interfaces
import me.kcra.takenaka.core.mapping.resolve.superClass
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView

private val logger = KotlinLogging.logger {}

/**
 * Corrects inconsistencies surrounding overridden methods.
 *
 * Intermediary & possibly more: overridden methods are not mapped, those are completed
 * Spigot: replaces wrong names with correct ones from supertypes
 *
 * This method expects [VanillaMappingContributor.NS_INTERFACES] and [VanillaMappingContributor.NS_SUPER] namespaces to be present.
 *
 * @param namespace the namespace that will be corrected
 */
fun MappingTree.completeMethodOverrides(namespace: String) {
    val namespaceId = getNamespaceId(namespace)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Namespace is not present in the mapping tree")
    }

    var correctionCount = 0
    classes.forEach { klass ->
        // FAST PATH: skip class when there are only java.* super types
        if (klass.superClass.startsWith("java/") && klass.interfaces.all { it.startsWith("java/") }) return@forEach

        // perf: turn off thread-safe lazy resolving, only one thread accesses these
        val klassName by lazy(LazyThreadSafetyMode.NONE) { klass.getDstName(namespaceId) }
        val klassSuperTypes by lazy(LazyThreadSafetyMode.NONE) { klass.superTypes }

        klass.methods.forEach originalEach@ { method ->
            val methodName = method.getDstName(namespaceId)
            if (methodName == null) {
                klassSuperTypes.forEach { superType ->
                    superType.methods.forEach superEach@ { superMethod ->
                        if (superMethod.srcName != method.srcName || superMethod.srcDesc != method.srcDesc) return@superEach
                        val superMethodName = superMethod.getDstName(namespaceId) ?: return@superEach

                        logger.debug { "corrected name of $klassName#$superMethodName for namespace $namespace" }
                        correctionCount++

                        method.setDstName(superMethodName, namespaceId)
                        return@originalEach // perf: return early when method is corrected
                    }
                }
            }
        }
    }

    logger.info { "corrected $correctionCount name(s) in namespace $namespace" }
}

/**
 * Corrects inconsistencies surrounding overridden methods in multiple namespaces at a time.
 *
 * Same constraints as for the [completeMethodOverrides] method apply.
 * This method might be slower than its non-batch counterpart when dealing with a lot of trees and/or few namespaces.
 *
 * @param namespaces the namespaces that will be corrected
 */
fun MappingTree.batchCompleteMethodOverrides(namespaces: List<String>) {
    val namespaceIds = namespaces.map { ns ->
        val namespaceId = getNamespaceId(ns)
        if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
            error("Namespace is not present in the mapping tree")
        }

        return@map namespaceId
    }

    var correctionCount = 0
    classes.forEach { klass ->
        // FAST PATH: skip class when there are only java.* super types
        if (klass.superClass.startsWith("java/") && klass.interfaces.all { it.startsWith("java/") }) return@forEach

        klass.methods.forEach originalEach@ { method ->
            var namespaceIdsToCorrect = namespaceIds.filter { method.getDstName(it) == null }

            if (namespaceIdsToCorrect.isNotEmpty()) {
                klass.superTypes.forEach { superType ->
                    superType.methods.forEach superEach@ { superMethod ->
                        if (superMethod.srcName != method.srcName || superMethod.srcDesc != method.srcDesc) return@superEach

                        namespaceIdsToCorrect = namespaceIdsToCorrect.filter { namespaceId ->
                            val superMethodName = superMethod.getDstName(namespaceId) ?: return@filter true

                            logger.debug { "corrected name of ${klass.getDstName(namespaceId)}#$superMethodName for namespace ${namespaces[namespaceIds.indexOf(namespaceId)]}" }
                            correctionCount++

                            method.setDstName(superMethodName, namespaceId)
                            return@filter false
                        }
                        if (namespaceIdsToCorrect.isEmpty()) return@originalEach // perf: return early when method is corrected on all namespaces
                    }
                }
            }
        }
    }

    logger.info { "corrected $correctionCount name(s) in namespaces ${namespaces.joinToString()}" }
}

/**
 * Recursively collects all **mapped** supertypes of the class.
 */
val MappingTreeView.ClassMappingView.superTypes: List<MappingTreeView.ClassMappingView>
    get() {
        val superTypes = mutableSetOf<String>()
        val mappedSuperTypes = mutableListOf<MappingTreeView.ClassMappingView>()

        fun processType(klassName: String) {
            if (superTypes.add(klassName)) {
                val klass = tree.getClass(klassName) ?: return
                mappedSuperTypes += klass

                processType(klass.superClass)
                klass.interfaces.forEach(::processType)
            }
        }

        processType(superClass)
        interfaces.forEach(::processType)

        return mappedSuperTypes
    }
