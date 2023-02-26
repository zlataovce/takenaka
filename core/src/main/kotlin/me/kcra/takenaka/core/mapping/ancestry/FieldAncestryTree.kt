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

typealias FieldAncestryTree = AncestryTree<MappingTree.FieldMapping, MemberKey>
typealias FieldAncestryNode = AncestryTree<MappingTree.FieldMapping, MemberKey>.Node

/**
 * Computes an ancestry tree of all fields in the ancestry tree of a class.
 *
 * @param klass the class node
 * @return the ancestry tree
 */
fun memberAncestryTreeOf(klass: ClassAncestryNode): FieldAncestryTree {
    val fieldTree = FieldAncestryTree()

    klass.mappings.forEach { (version, realKlass) ->
        val treeAllowedNamespaces = klass.allowedNamespaces[version] ?: error("Class ancestry tree does not map version ${version.id}")
        fieldTree.allowedNamespaces[version] = treeAllowedNamespaces

        realKlass.fields.forEach { field ->
            val fieldMappings = treeAllowedNamespaces
                .map { field.getDstName(it) to field.getDstDesc(it) }
                .filter(Pair<String, String>::valid)

            // do we have a node with at least one same key?
            // if we don't, we make a new node and append it to the tree
            val node = fieldTree[fieldMappings] ?: fieldTree.FieldAncestryNode().also { fieldTree += it }

            // append all mappings to the keys, ignoring duplicates (it's a set), and add a mapping entry to the node
            node.keys += fieldMappings
            node.mappings[version] = field
        }
    }

    return fieldTree
}
