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

// doing this is really weird, but it saves a lot of memory

/**
 * Interns (pools) element name and descriptor strings ([String.intern]).
 *
 * @param next the visitor to delegate to
 * @author Matouš Kučera
 */
class StringInterningAdapter(next: MappingVisitor) : ForwardingMappingVisitor(next) {
    override fun visitClass(srcName: String?): Boolean {
        return super.visitClass(srcName?.intern())
    }

    override fun visitField(srcName: String?, srcDesc: String?): Boolean {
        return super.visitField(srcName?.intern(), srcDesc?.intern())
    }

    override fun visitMethod(srcName: String?, srcDesc: String?): Boolean {
        return super.visitMethod(srcName?.intern(), srcDesc?.intern())
    }

    override fun visitDstName(targetKind: MappedElementKind?, namespace: Int, name: String?) {
        super.visitDstName(targetKind, namespace, name?.intern())
    }

    override fun visitDstDesc(targetKind: MappedElementKind?, namespace: Int, desc: String?) {
        super.visitDstDesc(targetKind, namespace, desc?.intern())
    }
}
