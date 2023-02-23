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

package me.kcra.takenaka.core.mapping.adapter

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor

/**
 * A [MappingVisitor] that filters out specified destination namespaces.
 *
 * @param next the visitor to delegate to
 * @param namespaces the filtered namespaces
 * @author Matouš Kučera
 */
class NamespaceFilter(next: MappingVisitor, vararg val namespaces: String) : ForwardingMappingVisitor(next) {
    // old id -> new id
    private var namespaceIds: Map<Int, Int> = emptyMap()

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        var namespaceIndex = 0
        namespaceIds = dstNamespaces.mapIndexedNotNull { i, ns -> if (ns !in namespaces) i to namespaceIndex++ else null }.toMap()

        super.visitNamespaces(srcNamespace, dstNamespaces.filter { it !in namespaces })
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        namespaceIds[namespace]?.let { super.visitDstName(targetKind, it, name) }
    }

    override fun visitDstDesc(targetKind: MappedElementKind, namespace: Int, desc: String?) {
        namespaceIds[namespace]?.let { super.visitDstDesc(targetKind, it, desc) }
    }
}