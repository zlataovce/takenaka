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
import net.fabricmc.mappingio.tree.MappingTreeView

private val logger = KotlinLogging.logger {}

/**
 * Filters out methods overridden from java/lang/Object (equals, toString, hashCode).
 */
fun MappingTree.removeObjectOverrides() {
    classes.forEach { klass ->
        if (klass.methods.removeIf { it.isEquals || it.isToString || it.isHashCode }) {
            logger.debug { "removed Object overrides of ${klass.srcName}" }
        }
    }
}

// we don't need to worry about querying srcName and srcDesc too much,
// they are just field getters in MemoryMappingTree

/**
 * Is this method an [Object.equals] method?
 */
inline val MappingTreeView.MethodMappingView.isEquals: Boolean
    get() = srcName == "equals" && srcDesc == "(Ljava/lang/Object;)Z"

/**
 * Is this method an [Object.toString] method?
 */
inline val MappingTreeView.MethodMappingView.isToString: Boolean
    get() = srcName == "toString" && srcDesc == "()Ljava/lang/String;"

/**
 * Is this method an [Object.hashCode] method?
 */
inline val MappingTreeView.MethodMappingView.isHashCode: Boolean
    get() = srcName == "hashCode" && srcDesc == "()I"
