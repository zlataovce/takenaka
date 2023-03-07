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

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree

/**
 * A function that wraps a mapping tree (maybe already wrapped) before contributors visit it.
 */
typealias InterceptBefore = (MappingVisitor) -> MappingVisitor

/**
 * A function that mutates a mapping tree.
 */
typealias InterceptAfter = (MemoryMappingTree) -> Unit

/**
 * A mapping tree builder.
 *
 * @author Matouš Kučera
 */
class MappingTreeBuilder {
    /**
     * The mapping contributors.
     */
    val contributors: MutableList<MappingContributor> = mutableListOf()

    /**
     * Functions that wrap the tree into a mapping visitor, maintains insertion order.
     */
    val interceptorsBefore: MutableList<InterceptBefore> = mutableListOf()

    /**
     * Functions that mutate the finalized tree, maintains insertion order.
     */
    val interceptorsAfter: MutableList<InterceptAfter> = mutableListOf()

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
    fun interceptBefore(block: InterceptBefore) {
        interceptorsBefore += block
    }

    /**
     * Appends a new tree mutating function.
     *
     * @param block the mutator
     */
    fun interceptAfter(block: InterceptAfter) {
        interceptorsAfter += block
    }

    /**
     * Builds the mapping tree.
     *
     * @return the mapping tree
     */
    fun toMappingTree(): MappingTree {
        val tree = MemoryMappingTree()
        val wrappedTree = interceptorsBefore.fold<InterceptBefore, MappingVisitor>(tree) { v, interceptor -> interceptor(v) }

        contributors.forEach { contributor -> contributor.accept(wrappedTree) }
        interceptorsAfter.forEach { interceptor -> interceptor(tree) }

        return tree
    }
}

/**
 * Builds a mapping tree with a builder.
 *
 * @param block the builder action
 * @return the tree
 */
inline fun buildMappingTree(block: MappingTreeBuilder.() -> Unit): MappingTree = MappingTreeBuilder().apply(block).toMappingTree()
