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

import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

private val logger = KotlinLogging.logger {}

/**
 * Corrects missing inner/anonymous class mappings.
 *
 * This method works on the principle of presuming that the owner part of the inner/anonymous class name should be completed with the owner's mapping.
 *
 * @param namespace the namespace, whose mappings are to be modified
 */
fun MappingTree.completeInnerClassNames(namespace: String) {
    val namespaceId = getNamespaceId(namespace)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Namespace is not present in the mapping tree")
    }

    var completionCount = 0
    classes.forEach { klass ->
        val dollarIndex = klass.srcName.lastIndexOf('$')
        if (dollarIndex == -1 || klass.getDstName(namespaceId) != null) return@forEach

        val owner = klass.srcName.substring(0, dollarIndex)

        val ownerKlass = getClass(owner)
        if (ownerKlass == null) {
            logger.debug { "inner class ${klass.srcName} without owner for namespace $namespace" }
            return@forEach
        }

        val ownerName = ownerKlass.getDstName(namespaceId)
        if (ownerName == null) {
            logger.debug { "inner class ${klass.srcName} without mapped owner ${ownerKlass.srcName} for namespace $namespace" }
            return@forEach
        }

        val name = klass.srcName.substring(dollarIndex + 1)

        klass.setDstName("$ownerName$$name", namespaceId)
        logger.debug { "completed inner class name ${klass.srcName} -> $ownerName$$name for namespace $namespace" }
        completionCount++
    }

    logger.info { "completed $completionCount inner class name(s) in namespace $namespace" }
}
