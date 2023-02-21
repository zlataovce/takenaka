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

import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.VersionedWorkspace
import net.fabricmc.mappingio.MappingVisitor

/**
 * A function for providing a list of mapping contributors for a single version.
 */
typealias ContributorProvider = (VersionedWorkspace, ObjectMapper) -> List<MappingContributor>

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
}

/**
 * A mapping contributor that wraps the mapping visitor before passing it to the delegate.
 *
 * @param contributor the delegate
 * @param wrap the wrapping function
 * @author Matouš Kučera
 */
class WrappingContributor(
    private val contributor: MappingContributor,
    val wrap: (MappingVisitor) -> MappingVisitor
) : MappingContributor by contributor {
    override fun accept(visitor: MappingVisitor) = contributor.accept(wrap(visitor))
}
