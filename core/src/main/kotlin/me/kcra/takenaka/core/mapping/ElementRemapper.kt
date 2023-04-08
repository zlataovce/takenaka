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

package me.kcra.takenaka.core.mapping

import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.commons.Remapper

/**
 * A function that selects a mapping from a tree.
 */
typealias ElementMapper = (MappingTreeView.ElementMappingView) -> String?

/**
 * A [Remapper] implementation that remaps class and class member names from a mapping tree.
 *
 * @param tree the mapping tree
 * @param elementMapper a function that selects the desired mapping for the element
 */
class ElementRemapper(val tree: MappingTreeView, val elementMapper: ElementMapper) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String =
        tree.getClass(owner)?.getMethod(name, descriptor)?.let(elementMapper) ?: name
    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String =
        mapFieldName(owner, name, descriptor)
    override fun mapFieldName(owner: String, name: String, descriptor: String): String =
        tree.getClass(owner)?.getField(name, descriptor)?.let(elementMapper) ?: name
    override fun map(internalName: String): String = tree.getClass(internalName)?.let(elementMapper) ?: internalName
}

/**
 * Replaces dots with slashes (e.g. qualified class name to internal name).
 *
 * @return the replaced string
 */
fun String.toInternalName(): String = replace('.', '/')

/**
 * Replaces slashes with dots (e.g. internal name to qualified class name).
 *
 * @return the replaced string
 */
fun String.fromInternalName(): String = replace('/', '.')
