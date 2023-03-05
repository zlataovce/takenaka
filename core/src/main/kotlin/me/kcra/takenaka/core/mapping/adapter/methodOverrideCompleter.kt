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
        // perf: turn off thread-safe lazy resolving, only one thread accesses these
        val klassName by lazy(LazyThreadSafetyMode.NONE) { klass.getDstName(namespaceId) }
        val klassSuperTypes by lazy(LazyThreadSafetyMode.NONE) { klass.superTypes }

        klass.methods.forEach { method ->
            val methodName = method.getDstName(namespaceId)
            if (methodName == null) {
                klassSuperTypes.forEach { superType ->
                    val superMethodName = superType.methods
                        .filter { it.srcName == method.srcName && it.srcDesc == method.srcDesc }
                        .firstNotNullOfOrNull { it.getDstName(namespaceId) }

                    if (superMethodName != null) {
                        logger.debug { "corrected name of $klassName#$superMethodName for namespace $namespace" }
                        correctionCount++

                        method.setDstName(superMethodName, namespaceId)
                    }
                }
            }
        }
    }

    logger.info { "corrected $correctionCount name(s) in namespace $namespace" }
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
