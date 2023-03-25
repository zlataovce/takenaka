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

import me.kcra.takenaka.core.mapping.matchers.isEquals
import me.kcra.takenaka.core.mapping.matchers.isHashCode
import me.kcra.takenaka.core.mapping.matchers.isToString
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

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
