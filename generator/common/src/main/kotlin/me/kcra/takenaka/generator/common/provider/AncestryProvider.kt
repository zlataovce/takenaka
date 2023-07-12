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
import me.kcra.takenaka.core.mapping.ancestry.ConstructorComputationMode
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
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @return the class ancestry tree
     */
    fun <T : MappingTreeView, C : ClassMappingView> klass(mappings: Map<Version, T>): AncestryTree<T, C>

    /**
     * Provides a field ancestry tree.
     *
     * @param node the class ancestry node
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @param F the mapping tree field member type
     * @return the field ancestry tree
     */
    fun <T : MappingTreeView, C : ClassMappingView, F : FieldMappingView> field(node: AncestryTree.Node<T, C>): AncestryTree<T, F>

    /**
     * Provides a method ancestry tree.
     *
     * @param node the class ancestry node
     * @param constructorMode the constructor handling mode
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @param M the mapping tree method member type
     * @return the method ancestry tree
     */
    fun <T : MappingTreeView, C : ClassMappingView, M : MethodMappingView> method(
        node: AncestryTree.Node<T, C>,
        constructorMode: ConstructorComputationMode = ConstructorComputationMode.EXCLUDE
    ): AncestryTree<T, M>
}
