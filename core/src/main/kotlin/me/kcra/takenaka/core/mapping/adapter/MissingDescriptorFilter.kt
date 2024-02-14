/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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

import mu.KotlinLogging
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor

private val logger = KotlinLogging.logger {}

/**
 * A mapping visitor that skips field and method mappings with a null descriptor.
 * This is adapted from the mapping-io [net.fabricmc.mappingio.adapter.MissingDescFilter] class, this one includes logging on top.
 *
 * @param next the visitor to delegate to
 * @author Matouš Kučera
 */
class MissingDescriptorFilter(next: MappingVisitor) : ForwardingMappingVisitor(next) {
    private var currentClass: String? = null

    override fun visitClass(srcName: String?): Boolean {
        currentClass = srcName

        return super.visitClass(srcName)
    }

    override fun visitField(srcName: String?, srcDesc: String?): Boolean {
        if (srcDesc == null) {
            logger.debug { "ignored null descriptor of field $srcName in class $currentClass" }
            return false
        }

        return super.visitField(srcName, srcDesc)
    }

    override fun visitMethod(srcName: String?, srcDesc: String?): Boolean {
        if (srcDesc == null) {
            logger.debug { "ignored null descriptor of method $srcName in class $currentClass" }
            return false
        }

        return super.visitMethod(srcName, srcDesc)
    }
}
