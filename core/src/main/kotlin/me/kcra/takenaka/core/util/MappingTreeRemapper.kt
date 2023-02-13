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

package me.kcra.takenaka.core.util

import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.commons.Remapper

/**
 * A function that selects a mapping from a tree.
 */
typealias MappingSelector = (MappingTree.ElementMapping) -> String?

/**
 * A [Remapper] implementation that remaps class and class member names from a mapping tree.
 *
 * @param tree the mapping tree
 * @param mappingSelector a function that selects the desired mapping for the element
 */
class MappingTreeRemapper(val tree: MappingTree, val mappingSelector: MappingSelector) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String =
        tree.getClass(owner)?.getMethod(name, descriptor)?.let(mappingSelector) ?: name
    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String =
        mapFieldName(owner, name, descriptor)
    override fun mapFieldName(owner: String, name: String, descriptor: String): String =
        tree.getClass(owner)?.getField(name, descriptor)?.let(mappingSelector) ?: name
    override fun map(internalName: String): String = tree.getClass(internalName)?.let(mappingSelector) ?: internalName
}