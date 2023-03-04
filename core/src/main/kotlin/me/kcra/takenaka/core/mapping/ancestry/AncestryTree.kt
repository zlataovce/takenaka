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

package me.kcra.takenaka.core.mapping.ancestry

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.VersionedMappingMap
import net.fabricmc.mappingio.tree.MappingTreeView.*

/**
 * A mapping ancestry tree.
 *
 * @param nodes the ancestry nodes
 * @property allowedNamespaces namespace IDs used for computing history, distinguished by version
 * @author Matouš Kučera
 */
class AncestryTree<T : ElementMappingView>(nodes: List<Node<T>>, val allowedNamespaces: Map<Version, List<Int>>) : List<AncestryTree.Node<T>> by nodes {
    /**
     * A node in the ancestry tree.
     * This represents one element (class, field, method) in multiple versions.
     *
     * @param tree the parent ancestry tree
     * @param mappings the class mappings, keyed by the version
     */
    class Node<T : ElementMappingView>(val tree: AncestryTree<*>, mappings: Map<Version, T>) : Map<Version, T> by mappings
}

/**
 * Computes an ancestry tree of all classes in the supplied versions.
 *
 * @param mappings the joined version mapping files
 * @param allowedNamespaces namespaces that are used in this tree for tracing history, not distinguished by version; empty if all namespaces should be considered
 * @return the ancestry tree
 */
fun classAncestryTreeOf(mappings: VersionedMappingMap, allowedNamespaces: List<String> = emptyList()): AncestryTree<ClassMappingView> {
    val nodes = mutableListOf<MutableMap<Version, ClassMappingView>>()
    val allAllowedNamespaces = mutableMapOf<Version, List<Int>>()

    mappings.forEach { (version, tree) ->
        var treeAllowedNamespaces = allowedNamespaces
            .map(tree::getNamespaceId)
            .filter { it != NULL_NAMESPACE_ID }
        if (treeAllowedNamespaces.isEmpty()) {
            treeAllowedNamespaces = (0 until tree.maxNamespaceId).toList()
        }
        allAllowedNamespaces[version] = treeAllowedNamespaces

        tree.classes.forEach { klass ->
            val classMappings = treeAllowedNamespaces.mapNotNull(klass::getDstName)
            // do we have a node with one or more equal names in the last version?
            // if we don't, make a new node and append it to the tree
            val node = nodes.find { node ->
                val (lastVersion, lastMapping) = node.entries.last()
                val lastNames = allAllowedNamespaces[lastVersion]?.mapNotNull(lastMapping::getDstName)
                    ?: error("Version ${version.id} has not been mapped yet, make sure mappings are sorted correctly")

                return@find lastNames.any(classMappings::contains)
            } ?: mutableMapOf<Version, ClassMappingView>().also(nodes::add)

            // add the entry for this version to the node
            node[version] = klass
        }
    }

    val treeNodes = mutableListOf<AncestryTree.Node<ClassMappingView>>()
    val tree = AncestryTree(treeNodes, allAllowedNamespaces)
    treeNodes += nodes.map { AncestryTree.Node(tree, it) }

    return tree
}

/**
 * Computes an ancestry tree of all fields in the supplied class ancestry node.
 *
 * @param klass the class node
 * @return the ancestry tree
 */
fun fieldAncestryTreeOf(klass: AncestryTree.Node<ClassMappingView>): AncestryTree<FieldMappingView> {
    val nodes = mutableListOf<MutableMap<Version, FieldMappingView>>()

    klass.forEach { (version, realKlass) ->
        val treeAllowedNamespaces = klass.tree.allowedNamespaces[version]
            ?: error("Version ${version.id} has not been mapped yet")

        realKlass.fields.forEach { field ->
            val fieldMappings = treeAllowedNamespaces
                .map { field.getDstName(it) to field.getDstDesc(it) }
                .filter { it.first != null && it.second != null }

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = nodes.find { node ->
                val (lastVersion, lastMapping) = node.entries.last()
                val lastNames = klass.tree.allowedNamespaces[lastVersion]
                    ?.map { lastMapping.getDstName(it) to lastMapping.getDstDesc(it) }
                    ?.filter { it.first != null && it.second != null }
                    ?: error("Version ${version.id} has not been mapped yet")

                return@find lastNames.any(fieldMappings::contains)
            } ?: mutableMapOf<Version, FieldMappingView>().also(nodes::add)

            // add the entry for this version to the node
            node[version] = field
        }
    }

    return AncestryTree(nodes.map { AncestryTree.Node(klass.tree, it) }, klass.tree.allowedNamespaces)
}

/**
 * Computes an ancestry tree of all methods in the supplied class ancestry node.
 *
 * @param klass the class node
 * @return the ancestry tree
 */
fun methodAncestryTreeOf(klass: AncestryTree.Node<ClassMappingView>): AncestryTree<MethodMappingView> {
    val nodes = mutableListOf<MutableMap<Version, MethodMappingView>>()

    klass.forEach { (version, realKlass) ->
        val treeAllowedNamespaces = klass.tree.allowedNamespaces[version]
            ?: error("Version ${version.id} has not been mapped yet")

        realKlass.methods.forEach { method ->
            val methodMappings = treeAllowedNamespaces
                .map { method.getDstName(it) to method.getDstDesc(it) }
                .filter { it.first != null && it.second != null }

            // do we have a node with one or more equal name-descriptor pairs in the last version?
            // if we don't, make a new node and append it to the tree
            val node = nodes.find { node ->
                val (lastVersion, lastMapping) = node.entries.last()
                val lastNames = klass.tree.allowedNamespaces[lastVersion]
                    ?.map { lastMapping.getDstName(it) to lastMapping.getDstDesc(it) }
                    ?.filter { it.first != null && it.second != null }
                    ?: error("Version ${version.id} has not been mapped yet")

                return@find lastNames.any(methodMappings::contains)
            } ?: mutableMapOf<Version, MethodMappingView>().also(nodes::add)

            // add the entry for this version to the node
            node[version] = method
        }
    }

    return AncestryTree(nodes.map { AncestryTree.Node(klass.tree, it) }, klass.tree.allowedNamespaces)
}
