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

package me.kcra.takenaka.core.mapping

import net.fabricmc.mappingio.MappingVisitor

/**
 * A mapping contributor.
 *
 * @author Matouš Kučera
 */
interface MappingContributor {
    /**
     * The target namespace of the contributor's mappings.
     */
    val targetNamespace: String

    /**
     * Visits its mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    fun accept(visitor: MappingVisitor)

    /**
     * Tries to warm up the contributor.
     *
     * This may pre-fetch any mappings, compute needed resources, but it is a no-op by default.
     */
    suspend fun warmup() {
    }
}

/**
 * A function that wraps a mapping visitor.
 */
typealias VisitorWrapper = (MappingVisitor) -> MappingVisitor

/**
 * A mapping contributor that wraps the mapping visitor before passing it to the delegate.
 *
 * @param contributor the delegate
 * @param wrap the wrapping function
 * @author Matouš Kučera
 */
class WrappingContributor(
    private val contributor: MappingContributor,
    val wrap: VisitorWrapper
) : MappingContributor by contributor {
    override fun accept(visitor: MappingVisitor) = contributor.accept(wrap(visitor))
}
