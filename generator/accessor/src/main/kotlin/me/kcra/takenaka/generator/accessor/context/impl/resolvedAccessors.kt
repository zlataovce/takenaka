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

package me.kcra.takenaka.generator.accessor.context.impl

import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.ancestry.impl.ClassAncestryNode
import me.kcra.takenaka.core.mapping.ancestry.nodeOf
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.model.ConstructorAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.*

/**
 * A class accessor with members resolved from their ancestry trees.
 *
 * @property model the class accessor model
 * @property node the class ancestry node
 * @property fields the field accessor models and ancestry nodes
 * @property constructors the constructor accessor models and ancestry nodes
 * @property methods the method accessor models and ancestry nodes
 */
data class ResolvedClassAccessor(
    val model: ClassAccessor,
    val node: ClassAncestryNode,
    val fields: List<ResolvedFieldAccessor>,
    val constructors: List<ResolvedConstructorAccessor>,
    val methods: List<ResolvedMethodAccessor>
)

/**
 * Member accessors with resolved ancestry nodes.
 *
 * @param nodes the ancestry nodes, multiple for chained models
 * @property overloadIndex overload index of the accessor
 */
class ResolvedMemberAccessor<M, T : MappingTreeView, E : ElementMappingView>(
    nodes: Map<M, AncestryTree.Node<T, E>>,
    val overloadIndex: Int
) : Map<M, AncestryTree.Node<T, E>> by nodes {
    /**
     * Creates a non-chained resolved accessor.
     *
     * @param model the accessor model
     * @param node the ancestry node
     * @param overloadIndex overload index of the accessor
     */
    constructor(model: M, node: AncestryTree.Node<T, E>, overloadIndex: Int) : this(mapOf(model to node), overloadIndex)

    /**
     * Whether this member is a chained one, i.e. multiple ancestry nodes.
     */
    val isChained: Boolean
        get() = size > 1

    /**
     * The last accessor model, used for visual representation.
     */
    val model: M by lazy(keys::last)

    /**
     * The merged ancestry node.
     */
    val mergedNode: AncestryTree.Node<T, E> by lazy {
        nodeOf(*values.toTypedArray())
    }

    // destructuring utilities
    operator fun component1() = model
    operator fun component2() = mergedNode
    operator fun component3() = overloadIndex
}

typealias ResolvedFieldAccessor = ResolvedMemberAccessor<FieldAccessor, out MappingTreeView, out FieldMappingView>
typealias ResolvedConstructorAccessor = ResolvedMemberAccessor<ConstructorAccessor, out MappingTreeView, out MethodMappingView>
typealias ResolvedMethodAccessor = ResolvedMemberAccessor<MethodAccessor, out MappingTreeView, out MethodMappingView>