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
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

private val logger = KotlinLogging.logger {}

/**
 * Filters out classes and members without modifiers.
 *
 * This filter relies on the presence of [VanillaMappingContributor.NS_MODIFIERS], so make sure you visit [VanillaMappingContributor] beforehand.
 *
 * These may include client classes or erroneous mappings.
 */
fun MappingTree.filterWithModifiers() {
    val namespaceId = getNamespaceId(VanillaMappingContributor.NS_MODIFIERS)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Mapping tree has not visited modifiers before")
    }

    val classesForRemoval = mutableListOf<String>()
    classes.forEach { klass ->
        if (klass.getDstName(namespaceId) == null) {
            logger.debug { "removed class ${klass.srcName}, missing modifiers" }
            classesForRemoval += klass.srcName
            return@forEach
        }

        val fieldsForRemoval = mutableMapOf<String, String>()
        klass.fields.forEach { field ->
            if (field.getDstName(namespaceId) == null) {
                logger.debug { "removed field ${klass.srcName}#${field.srcName} ${field.srcDesc}, missing modifiers" }
                fieldsForRemoval += field.srcName to field.srcDesc
            }
        }
        fieldsForRemoval.forEach { (name, desc) -> klass.removeField(name, desc) }
        logger.debug { "removed ${fieldsForRemoval.size} field(s) without modifiers in class ${klass.srcName}" }

        val methodsForRemoval = mutableMapOf<String, String>()
        klass.methods.forEach { method ->
            if (method.getDstName(namespaceId) == null) {
                logger.debug { "removed method ${klass.srcName}#${method.srcName}${method.srcDesc}, missing modifiers" }
                methodsForRemoval += method.srcName to method.srcDesc
            }
        }
        methodsForRemoval.forEach { (name, desc) -> klass.removeMethod(name, desc) }
        logger.debug { "removed ${methodsForRemoval.size} method(s) without modifiers in class ${klass.srcName}" }
    }

    classesForRemoval.forEach { removeClass(it) }
    logger.info { "removed ${classesForRemoval.size} class(es) without modifiers" }
}
