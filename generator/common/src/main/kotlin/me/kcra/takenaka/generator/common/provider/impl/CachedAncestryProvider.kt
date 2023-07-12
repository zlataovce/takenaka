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

package me.kcra.takenaka.generator.common.provider.impl

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.MappingsMap
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.ancestry.impl.*
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import net.fabricmc.mappingio.tree.MappingTreeView
import java.util.*

/**
 * An [AncestryProvider] implementation that caches results from a downstream provider.
 *
 * @property next the provider that requests are forwarded to
 * @author Matouš Kučera
 */
class CachedAncestryProvider(val next: AncestryProvider) : AncestryProvider {
    /**
     * A class ancestry tree cache.
     */
    private val klassTrees = WeakHashMap<MappingsMap, ClassAncestryTree>()

    /**
     * A field ancestry tree cache.
     */
    private val fieldTrees = WeakHashMap<ClassAncestryNode, FieldAncestryTree>()

    /**
     * A constructor ancestry tree cache.
     */
    private val ctorTrees = WeakHashMap<ClassAncestryNode, MethodAncestryTree>()

    /**
     * A method ancestry tree cache.
     */
    private val methodTrees = WeakHashMap<ClassAncestryNode, MethodAncestryTree>()

    /**
     * Provides a class ancestry tree.
     *
     * @param mappings the mapping set
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @return the class ancestry tree
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : MappingTreeView, C : MappingTreeView.ClassMappingView> klass(mappings: Map<Version, T>): AncestryTree<T, C> {
        return klassTrees.getOrPut(mappings) { next.klass(mappings) } as AncestryTree<T, C>
    }

    /**
     * Provides a field ancestry tree.
     *
     * @param node the class ancestry node
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @param F the mapping tree field member type
     * @return the field ancestry tree
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : MappingTreeView, C : MappingTreeView.ClassMappingView, F : MappingTreeView.FieldMappingView> field(
        node: AncestryTree.Node<T, C>
    ): AncestryTree<T, F> {
        return fieldTrees.getOrPut(node) { next.field(node) } as AncestryTree<T, F>
    }

    /**
     * Provides a constructor ancestry tree.
     *
     * @param node the class ancestry node
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @param M the mapping tree method member type
     * @return the constructor ancestry tree
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : MappingTreeView, C : MappingTreeView.ClassMappingView, M : MappingTreeView.MethodMappingView> constructor(
        node: AncestryTree.Node<T, C>
    ): AncestryTree<T, M> {
        return ctorTrees.getOrPut(node) { next.field(node) } as AncestryTree<T, M>
    }

    /**
     * Provides a method ancestry tree.
     *
     * @param node the class ancestry node
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @param M the mapping tree method member type
     * @return the method ancestry tree
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : MappingTreeView, C : MappingTreeView.ClassMappingView, M : MappingTreeView.MethodMappingView> method(
        node: AncestryTree.Node<T, C>
    ): AncestryTree<T, M> {
        return methodTrees.getOrPut(node) { next.field(node) } as AncestryTree<T, M>
    }
}