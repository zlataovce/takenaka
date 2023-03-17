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
fun MappingTree.removeElementsWithoutModifiers() {
    val namespaceId = getNamespaceId(VanillaMappingContributor.NS_MODIFIERS)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Mapping tree has not visited modifiers before")
    }

    var removedClasses = 0
    classes.removeIf klassRemove@ { klass ->
        if (klass.getDstName(namespaceId) == null) {
            logger.debug { "removed class ${klass.srcName}, missing modifiers" }
            removedClasses++

            return@klassRemove true
        }

        var removedFields = 0
        klass.fields.removeIf fieldRemove@ { field ->
            val removed = field.getDstName(namespaceId) == null
            if (removed) {
                logger.debug { "removed field ${klass.srcName}#${field.srcName} ${field.srcDesc}, missing modifiers" }
                removedFields++
            }

            return@fieldRemove removed
        }
        logger.debug { "removed $removedFields field(s) without modifiers in class ${klass.srcName}" }

        var removedMethods = 0
        klass.methods.removeIf methodRemove@ { method ->
            val removed = method.getDstName(namespaceId) == null
            if (removed) {
                logger.debug { "removed method ${klass.srcName}#${method.srcName}${method.srcDesc}, missing modifiers" }
                removedMethods++
            }

            return@methodRemove removed
        }
        logger.debug { "removed $removedMethods method(s) without modifiers in class ${klass.srcName}" }

        return@klassRemove false
    }

    logger.info { "removed $removedClasses class(es) without modifiers" }
}
