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

import org.objectweb.asm.Opcodes
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

private val logger = KotlinLogging.logger {}

/**
 * Filters out synthetic classes and members.
 *
 * This filter relies on the presence of [VanillaMappingContributor.NS_MODIFIERS], so make sure you visit [VanillaMappingContributor] beforehand.
 */
fun MappingTree.filterNonSynthetic() {
    val namespaceId = getNamespaceId(VanillaMappingContributor.NS_MODIFIERS)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Mapping tree has not visited modifiers before")
    }

    fun MappingTree.ElementMapping.getModifiers(): Int = getDstName(namespaceId)?.toIntOrNull() ?: 0

    val classesForRemoval = mutableListOf<MappingTree.ClassMapping>()
    classes.forEach { klass ->
        if ((klass.getModifiers() and Opcodes.ACC_SYNTHETIC) != 0) {
            logger.debug { "removed class ${klass.srcName}, synthetic" }
            classesForRemoval += klass
            return@forEach
        }

        val fieldsForRemoval = mutableListOf<MappingTree.FieldMapping>()
        klass.fields.forEach { field ->
            if ((field.getModifiers() and Opcodes.ACC_SYNTHETIC) != 0) {
                logger.debug { "removed field ${klass.srcName}#${field.srcName} ${field.srcDesc}, synthetic" }
                fieldsForRemoval += field
            }
        }
        fieldsForRemoval.forEach { field -> klass.removeField(field.srcName, field.srcDesc) }
        logger.debug { "removed ${fieldsForRemoval.size} synthetic field(s) in class ${klass.srcName}" }

        val methodsForRemoval = mutableListOf<MappingTree.MethodMapping>()
        klass.methods.forEach { method ->
            if ((method.getModifiers() and Opcodes.ACC_SYNTHETIC) != 0) {
                logger.debug { "removed method ${klass.srcName}#${method.srcName}${method.srcDesc}, synthetic" }
                methodsForRemoval += method
            }
        }
        methodsForRemoval.forEach { method -> klass.removeMethod(method.srcName, method.srcDesc) }
        logger.debug { "removed ${methodsForRemoval.size} synthetic method(s) in class ${klass.srcName}" }
    }

    classesForRemoval.forEach { klass -> removeClass(klass.srcName) }
    logger.info { "removed ${classesForRemoval.size} synthetic class(es)" }
}
