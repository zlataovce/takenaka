/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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
 * @param mapper a function that selects the desired mapping for the element
 */
open class ElementRemapper(val tree: MappingTreeView, val mapper: ElementMapper) : Remapper() {
    /**
     * Maps the method name based on the provided owner, name, and descriptor.
     *
     * @param owner the owner of the method
     * @param name the name of the method
     * @param descriptor the descriptor of the method
     * @return the mapped method name, or the original name if a mapping is not found or selected by [mapper]
     */
    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        return tree.getClass(owner)?.getMethod(name, descriptor)?.let(mapper) ?: name
    }

    /**
     * Maps the record component name based on the provided owner, name, and descriptor.
     *
     * @param owner the owner of the record component
     * @param name the name of the record component
     * @param descriptor the descriptor of the record component
     * @return the mapped record component name
     */
    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String {
        return mapFieldName(owner, name, descriptor)
    }

    /**
     * Maps the field name based on the provided owner, name, and descriptor.
     *
     * @param owner the owner of the field
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     * @return the mapped field name
     */
    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return tree.getClass(owner)?.getField(name, descriptor)?.let(mapper) ?: name
    }

    /**
     * Maps the given internal name to its corresponding mapping using the [mapper] function.
     *
     * @param internalName the internal name to be mapped
     * @return the mapped value, the original internal name if it wasn't found in the [tree] or [mapper] returned null
     */
    override fun map(internalName: String): String {
        return tree.getClass(internalName)?.let(mapper) ?: internalName
    }
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
