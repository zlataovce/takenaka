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

import net.fabricmc.mappingio.tree.MappingTree

typealias MethodAncestryTree = AncestryTree<MappingTree.MethodMapping, MemberKey>
typealias MethodAncestryNode = AncestryTree<MappingTree.MethodMapping, MemberKey>.Node

/**
 * Computes an ancestry tree of all methods in the ancestry tree of a class.
 *
 * @param klass the class node
 * @return the ancestry tree
 */
fun methodAncestryTreeOf(klass: ClassAncestryNode): MethodAncestryTree {
    val methodTree = MethodAncestryTree()

    klass.mappings.forEach { (version, realKlass) ->
        val treeAllowedNamespaces = klass.allowedNamespaces[version] ?: error("Class ancestry tree does not map version ${version.id}")
        methodTree.allowedNamespaces[version] = treeAllowedNamespaces

        realKlass.methods.forEach { method ->
            val methodMappings = treeAllowedNamespaces
                .map { method.getDstName(it) to method.getDstDesc(it) }
                .filter(Pair<String, String>::valid)

            // do we have a node with at least one same key?
            // if we don't, we make a new node and append it to the tree
            val node = methodTree[methodMappings] ?: methodTree.MethodAncestryNode().also { methodTree += it }

            // append all mappings to the keys, ignoring duplicates (it's a set), and add a mapping entry to the node
            node.keys += methodMappings
            node.mappings[version] = method
        }
    }

    return methodTree
}
