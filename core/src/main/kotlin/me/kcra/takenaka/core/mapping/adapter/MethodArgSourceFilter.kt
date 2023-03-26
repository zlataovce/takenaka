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

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor

/**
 * A [MappingVisitor] that filters out source (obfuscated) names of method parameters.
 *
 * In most cases, you won't have obfuscated names of method parameters, so they are a filler that mapping vendors put in (like in Searge).
 *
 * @param next the visitor to delegate to
 * @author Matouš Kučera
 */
class MethodArgSourceFilter(next: MappingVisitor) : ForwardingMappingVisitor(next) {
    override fun visitMethodArg(argPosition: Int, lvIndex: Int, srcName: String?): Boolean {
        return super.visitMethodArg(argPosition, lvIndex, null)
    }
}
