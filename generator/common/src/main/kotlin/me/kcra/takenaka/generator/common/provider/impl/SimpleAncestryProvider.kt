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
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.ancestry.impl.ConstructorComputationMode
import me.kcra.takenaka.core.mapping.ancestry.impl.classAncestryTreeOf
import me.kcra.takenaka.core.mapping.ancestry.impl.fieldAncestryTreeOf
import me.kcra.takenaka.core.mapping.ancestry.impl.methodAncestryTreeOf
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import me.kcra.takenaka.generator.common.provider.MappingProvider
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.*

/**
 * A [MappingProvider] implementation that provides ancestry trees with the [me.kcra.takenaka.core.mapping.ancestry.impl] package.
 *
 * @property indexNs namespace that contains node indices, null if there are none, *does not need to exist - ignored*
 * @property allowedNamespaces namespaces that are used in this tree for tracing history, not distinguished by version; empty if all namespaces should be considered
 * @author Matouš Kučera
 */
class SimpleAncestryProvider(val indexNs: String?, val allowedNamespaces: List<String>) : AncestryProvider {
    /**
     * Provides a class ancestry tree.
     *
     * @param mappings the mapping set
     * @param T the mapping tree type
     * @param C the mapping tree class member type
     * @return the class ancestry tree
     */
    override fun <T : MappingTreeView, C : ClassMappingView> klass(mappings: Map<Version, T>): AncestryTree<T, C> {
        return classAncestryTreeOf(mappings, indexNs, allowedNamespaces)
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
    override fun <T : MappingTreeView, C : ClassMappingView, F : FieldMappingView> field(node: AncestryTree.Node<T, C>): AncestryTree<T, F> {
        return fieldAncestryTreeOf(node)
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
    override fun <T : MappingTreeView, C : ClassMappingView, M : MethodMappingView> constructor(node: AncestryTree.Node<T, C>): AncestryTree<T, M> {
        return methodAncestryTreeOf(node, constructorMode = ConstructorComputationMode.ONLY)
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
    override fun <T : MappingTreeView, C : ClassMappingView, M : MethodMappingView> method(node: AncestryTree.Node<T, C>): AncestryTree<T, M> {
        return methodAncestryTreeOf(node)
    }
}