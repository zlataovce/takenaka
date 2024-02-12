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

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree

/**
 * A function that wraps a mapping tree (maybe already wrapped) before contributors visit it.
 */
typealias MapperIntercept = (MappingVisitor) -> MappingVisitor

/**
 * A mapping tree builder.
 *
 * @author Matouš Kučera
 */
class MappingTreeBuilder {
    /**
     * The mapping contributors.
     */
    var contributors = mutableListOf<MappingContributor>()

    /**
     * Functions that wrap the tree into a mapping visitor, maintains insertion order.
     */
    var interceptors = mutableListOf<MapperIntercept>()

    /**
     * Appends mapping contributors.
     *
     * @param items the contributors
     */
    fun contributor(vararg items: MappingContributor) {
        contributors += items
    }

    /**
     * Appends mapping contributors.
     *
     * @param items the contributors
     */
    fun contributor(items: List<MappingContributor>) {
        contributors += items
    }

    /**
     * Appends a mapping contributor, wrapped with [wrap].
     *
     * @param item the contributor
     * @param wrap the wrapping function
     */
    fun contributor(item: MappingContributor, wrap: VisitorWrapper) {
        contributors += WrappingContributor(item, wrap)
    }

    /**
     * Appends a new tree wrapping function.
     *
     * @param block the wrapping function
     */
    fun intercept(block: MapperIntercept) {
        interceptors += block
    }

    /**
     * Builds the mapping tree.
     *
     * @return the mapping tree
     */
    fun toMappingTree(): MappingTree = MemoryMappingTree().apply {
        val wrappedTree = interceptors.fold<MapperIntercept, MappingVisitor>(this) { v, interceptor -> interceptor(v) }

        contributors.forEach { contributor -> contributor.accept(wrappedTree) }
    }
}

/**
 * Builds a mapping tree with a builder.
 *
 * @param block the builder action
 * @return the tree
 */
inline fun buildMappingTree(block: MappingTreeBuilder.() -> Unit): MappingTree = MappingTreeBuilder().apply(block).toMappingTree()
