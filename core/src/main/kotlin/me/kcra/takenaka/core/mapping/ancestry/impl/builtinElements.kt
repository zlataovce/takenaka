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

package me.kcra.takenaka.core.mapping.ancestry.impl

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.ancestry.ConstructorComputationMode
import me.kcra.takenaka.core.mapping.ancestry.NameDescriptorPair
import me.kcra.takenaka.core.mapping.ancestry.buildAncestryTree
import me.kcra.takenaka.core.mapping.matchers.isConstructor
import me.kcra.takenaka.core.mapping.util.dstNamespaceIds
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * An alias to shorten generics.
 */
typealias ClassAncestryTree = AncestryTree<out MappingTreeView, out MappingTreeView.ClassMappingView>

/**
 * An alias to shorten generics.
 */
typealias MutableClassAncestryTree = AncestryTree<MappingTree, MappingTree.ClassMapping>

/**
 * An alias to shorten generics.
 */
typealias ClassAncestryNode = AncestryTree.Node<out MappingTreeView, out MappingTreeView.ClassMappingView>

/**
 * An alias to shorten generics.
 */
typealias MutableClassAncestryNode = AncestryTree.Node<MappingTree, MappingTree.ClassMapping>

/**
 * Computes an ancestry tree of all classes in the supplied versions.
 *
 * @param mappings the joined version mapping files
 * @param indexNs namespace that contains node indices, null if there are none, *does not need to exist - ignored*
 * @param allowedNamespaces namespaces that are used in this tree for tracing history, not distinguished by version; empty if all namespaces should be considered
 * @param T the mapping tree type
 * @param C the mapping tree class member type
 * @return the ancestry tree
 */
fun <T : MappingTreeView, C : MappingTreeView.ClassMappingView> classAncestryTreeOf(
    mappings: Map<Version, T>,
    indexNs: String? = null,
    allowedNamespaces: List<String> = emptyList()
): AncestryTree<T, C> = buildAncestryTree {
    trees += mappings

    // convert to sorted map to ensure proper ordering
    mappings.toSortedMap().forEach treeEach@ { (version, tree) ->
        val treeAllowedNamespaces = allowedNamespaces
            .map(tree::getNamespaceId)
            .filter { it != MappingTreeView.NULL_NAMESPACE_ID }
            .ifEmpty { tree.dstNamespaceIds.toList() }
            .toTypedArray()

        this@buildAncestryTree.allowedNamespaces[version] = treeAllowedNamespaces

        val indexNsId = indexNs?.let(tree::getNamespaceId) ?: MappingTreeView.NULL_NAMESPACE_ID
        if (indexNsId != MappingTreeView.NULL_NAMESPACE_ID) {
            this@buildAncestryTree.indexNamespaces[version] = indexNsId
        }

        @Suppress("UNCHECKED_CAST")
        (tree.classes as Collection<C>).forEach { klass ->
            // try to resolve a node by its index
            if (indexNsId != MappingTreeView.NULL_NAMESPACE_ID) {
                val nodeIndex = klass.getDstName(indexNsId)?.toIntOrNull()
                if (nodeIndex != null) {
                    val node = findByIndex(nodeIndex)

                    node[version] = klass // it's not necessary to fill in lastNames
                    return@forEach
                }
            }

            val classMappings = treeAllowedNamespaces.mapNotNullTo(mutableSetOf(), klass::getDstName)
            val classMappingsArray = classMappings.toTypedArray() // perf: use array due to marginally better iteration performance

            // do we have a node with one or more equal names in the last version?
            // if we don't, make a new node and append it to the tree
            val node = findOrEmpty { node ->
                val (lastVersion, lastMapping) = node.last
                val lastNames = (node.lastNames ?: this@buildAncestryTree.allowedNamespaces[lastVersion]?.mapNotNull(lastMapping::getDstName))
                    ?: error("Version ${version.id} has not been mapped yet, make sure mappings are sorted correctly")

                return@findOrEmpty classMappingsArray.any(lastNames::contains)
            }

            // add the entry for this version to the node
            val lastValue = node.put(version, klass)
            if (lastValue == null) {
                node.lastNames = classMappings
            }
        }
    }
}

/**
 * An alias to shorten generics.
 */
typealias FieldAncestryTree = AncestryTree<out MappingTreeView, out MappingTreeView.FieldMappingView>

/**
 * An alias to shorten generics.
 */
typealias MutableFieldAncestryTree = AncestryTree<MappingTree, MappingTree.FieldMapping>

/**
 * An alias to shorten generics.
 */
typealias FieldAncestryNode = AncestryTree.Node<out MappingTreeView, out MappingTreeView.FieldMappingView>

/**
 * An alias to shorten generics.
 */
typealias MutableFieldAncestryNode = AncestryTree.Node<MappingTree, MappingTree.FieldMapping>

/**
 * Computes an ancestry tree of all fields in the supplied class ancestry node.
 *
 * @param klass the class node
 * @param T the mapping tree type
 * @param C the mapping tree class member type
 * @param F the mapping tree field member type
 * @return the ancestry tree
 */
fun <T : MappingTreeView, C : MappingTreeView.ClassMappingView, F : MappingTreeView.FieldMappingView> fieldAncestryTreeOf(klass: AncestryTree.Node<T, C>): AncestryTree<T, F> = buildAncestryTree {
    inheritTrees(klass.tree)
    inheritNamespaces(klass.tree)

    klass.forEach klassEach@ { (version, realKlass) ->
        val treeAllowedNamespaces = klass.tree.allowedNamespaces[version]
            ?: error("Version ${version.id} has not been mapped yet")

        val indexNsId = klass.tree.indexNamespaces[version] ?: MappingTreeView.NULL_NAMESPACE_ID

        @Suppress("UNCHECKED_CAST")
        (realKlass.fields as Collection<F>).forEach { field ->
            // try to resolve a node by its index
            if (indexNsId != MappingTreeView.NULL_NAMESPACE_ID) {
                val nodeIndex = field.getDstName(indexNsId)?.toIntOrNull()
                if (nodeIndex != null) {
                    val node = findByIndex(nodeIndex)

                    node[version] = field // it's not necessary to fill in lastNames
                    return@forEach
                }
            }

            val fieldMappings = treeAllowedNamespaces
                .mapNotNullTo(mutableSetOf()) { ns ->
                    val name = field.getDstName(ns)
                        ?: return@mapNotNullTo null
                    val desc = field.getDstDesc(ns)
                        ?: return@mapNotNullTo null

                    name to desc
                }

            val fieldMappingsArray = fieldMappings.toTypedArray() // perf: use array due to marginally better iteration performance

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = findOrEmpty { node ->
                val (lastVersion, lastMapping) = node.last
                val lastNames = node.lastNames
                    ?: this@buildAncestryTree.allowedNamespaces[lastVersion]?.mapNotNull { ns ->
                        val name = lastMapping.getDstName(ns)
                            ?: return@mapNotNull null
                        val desc = lastMapping.getDstDesc(ns)
                            ?: return@mapNotNull null

                        name to desc
                    }
                    ?: error("Version ${lastVersion.id} has not been mapped yet")

                return@findOrEmpty fieldMappingsArray.any(lastNames::contains)
            }

            // add the entry for this version to the node
            val lastValue = node.put(version, field)
            if (lastValue == null) {
                node.lastNames = fieldMappings
            }
        }
    }
}

/**
 * An alias to shorten generics.
 */
typealias MethodAncestryTree = AncestryTree<out MappingTreeView, out MappingTreeView.MethodMappingView>

/**
 * An alias to shorten generics.
 */
typealias MutableMethodAncestryTree = AncestryTree<MappingTree, MappingTree.MethodMapping>

/**
 * An alias to shorten generics.
 */
typealias MethodAncestryNode = AncestryTree.Node<out MappingTreeView, out MappingTreeView.MethodMappingView>

/**
 * An alias to shorten generics.
 */
typealias MutableMethodAncestryNode = AncestryTree.Node<MappingTree, MappingTree.MethodMapping>

/**
 * Computes an ancestry tree of all methods in the supplied class ancestry node.
 *
 * @param klass the class node
 * @param constructorMode constructor handling behavior setting
 * @param T the mapping tree type
 * @param C the mapping tree class member type
 * @param M the mapping tree method member type
 * @return the ancestry tree
 */
fun <T : MappingTreeView, C : MappingTreeView.ClassMappingView, M : MappingTreeView.MethodMappingView>  methodAncestryTreeOf(
    klass: AncestryTree.Node<T, C>,
    constructorMode: ConstructorComputationMode = ConstructorComputationMode.EXCLUDE
): AncestryTree<T, M> = buildAncestryTree {
    inheritTrees(klass.tree)
    inheritNamespaces(klass.tree)

    klass.forEach klassEach@ { (version, realKlass) ->
        val treeAllowedNamespaces = klass.tree.allowedNamespaces[version]
            ?: error("Version ${version.id} has not been mapped yet")

        val indexNsId = klass.tree.indexNamespaces[version] ?: MappingTreeView.NULL_NAMESPACE_ID
        @Suppress("UNCHECKED_CAST")
        (realKlass.methods as Collection<M>).forEach { method ->
            val isConstructor = method.isConstructor
            when (constructorMode) {
                ConstructorComputationMode.EXCLUDE -> {
                    if (isConstructor) return@forEach
                }

                ConstructorComputationMode.ONLY -> {
                    if (!isConstructor) return@forEach
                }

                ConstructorComputationMode.INCLUDE -> {}
            }

            // try to resolve a node by its index
            if (indexNsId != MappingTreeView.NULL_NAMESPACE_ID) {
                val nodeIndex = method.getDstName(indexNsId)?.toIntOrNull()
                if (nodeIndex != null) {
                    val node = findByIndex(nodeIndex)

                    node[version] = method // it's not necessary to fill in lastNames
                    return@forEach
                }
            }

            val methodMappings = treeAllowedNamespaces
                .mapNotNullTo(mutableSetOf()) { ns ->
                    val name = if (isConstructor) "<init>" else (method.getDstName(ns) ?: return@mapNotNullTo null)
                    val desc = method.getDstDesc(ns)
                        ?: return@mapNotNullTo null

                    name to desc
                }

            val methodMappingsArray = methodMappings.toTypedArray() // perf: use array due to marginally better iteration performance

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = findOrEmpty { node ->
                val (lastVersion, lastMapping) = node.last
                val isLastConstructor = lastMapping.isConstructor

                // FAST PATH: method type mismatch, so skip
                if (isConstructor != isLastConstructor) return@findOrEmpty false

                val lastNames = node.lastNames
                    ?: this@buildAncestryTree.allowedNamespaces[lastVersion]?.mapNotNull { ns ->
                        val name = if (isLastConstructor) "<init>" else (lastMapping.getDstName(ns) ?: return@mapNotNull null)
                        val desc = lastMapping.getDstDesc(ns)
                            ?: return@mapNotNull null

                        name to desc
                    }
                    ?: error("Version ${lastVersion.id} has not been mapped yet")

                return@findOrEmpty methodMappingsArray.any(lastNames::contains)
            }

            // add the entry for this version to the node
            val lastValue = node.put(version, method)
            if (lastValue == null) {
                node.lastNames = methodMappings
            }
        }
    }
}

/**
 * Searches for a member node in a tree, an alternative to [AncestryTree.get] if you don't know the descriptor or only the arguments.
 *
 * @param name the mapped field name
 * @param descriptor the mapped descriptor, may be partial, descriptors are not checked if null
 * @param version the version in which [name] is located, presumes last (newest) if null
 * @param T the mapping tree type
 * @param M the mapping tree member type
 */
fun <T : MappingTreeView, M : MappingTreeView.MemberMappingView> AncestryTree<T, M>.find(
    name: String,
    descriptor: String? = null,
    version: Version? = null
): AncestryTree.Node<T, M>? = find stdFind@ { node ->
    if (version == null) {
        @Suppress("UNCHECKED_CAST") // should always be NameDescriptorPair, since it's a field or a method
        (node.lastNames as Set<NameDescriptorPair>).any { (pairName, pairDesc) ->
            pairName == name && (descriptor == null || pairDesc.startsWith(descriptor))
        }
    } else {
        val member = node[version] ?: return@stdFind false

        node.tree.allowedNamespaces[version]?.any { ns -> member.getDstName(ns) == name && (descriptor == null || member.getDstDesc(ns).startsWith(descriptor)) } == true
    }
}
