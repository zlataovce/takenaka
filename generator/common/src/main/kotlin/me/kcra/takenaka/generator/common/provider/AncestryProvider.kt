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

package me.kcra.takenaka.generator.common.provider

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.*

/**
 * A provider of ancestry trees required for generation.
 *
 * @author Matouš Kučera
 */
interface AncestryProvider {
    /**
     * Provides a class ancestry tree.
     *
     * @param mappings the mapping set
     * @return the class ancestry tree
     */
    fun <T : MappingTreeView, C : ClassMappingView> klass(mappings: Map<Version, T>): AncestryTree<T, C>

    /**
     * Provides a field ancestry tree.
     *
     * @param node the class ancestry node
     * @return the field ancestry tree
     */
    fun <T : MappingTreeView, C : ClassMappingView, F : FieldMappingView> field(node: AncestryTree.Node<T, C>): AncestryTree<T, F>

    /**
     * Provides a method ancestry tree.
     *
     * @param node the class ancestry node
     * @return the method ancestry tree
     */
    fun <T : MappingTreeView, C : ClassMappingView, M : MethodMappingView> method(node: AncestryTree.Node<T, C>): AncestryTree<T, M>

    /**
     * Provides a constructor ancestry tree.
     *
     * @param node the class ancestry node
     * @return the constructor ancestry tree
     */
    fun <T : MappingTreeView, C : ClassMappingView, M : MethodMappingView> constructor(node: AncestryTree.Node<T, C>): AncestryTree<T, M>
}
