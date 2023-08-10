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

package me.kcra.takenaka.generator.web

import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.fromInternalName

/**
 * A function that maps internal names to hyperlinks.
 */
typealias LinkMapper = (String) -> String

/**
 * A class for remapping elements in a contextual environment.
 *
 * @property nameRemapper the remapper for class names.
 * @property linkRemapper the remapper for link class names
 * @property classIndex the search index for searching foreign class references
 * @property linkMapper the mapper for mapping internal names to hyperlinks
 * @author Matouš Kučera
 */
class ContextualElementRemapper(
    var nameRemapper: ElementRemapper,
    var linkRemapper: ElementRemapper?,
    val classIndex: ClassSearchIndex?,
    var linkMapper: LinkMapper
) {
    /**
     * Maps the given internal name to a hyperlink.
     *
     * @param internalName the internal name to be mapped
     * @return the mapped link
     */
    fun mapLink(internalName: String): String = linkMapper(internalName)

    /**
     * Remaps a class name and creates a link if a mapping has been found.
     *
     * @param internalName the internal name of the class to be remapped
     * @return the remapped class name, a link if it was found
     */
    fun mapAndLink(internalName: String): String {
        val foreignUrl = classIndex?.linkClass(internalName)
        if (foreignUrl != null) {
            return """<a href="$foreignUrl">${internalName.substringAfterLast('/')}</a>"""
        }

        val remappedName = nameRemapper.tree.getClass(internalName)?.let(nameRemapper.mapper)
            ?: return internalName.fromInternalName()

        val linkName = linkRemapper?.map(internalName) ?: remappedName
        return """<a href="${mapLink(linkName)}">${remappedName.substringAfterLast('/')}</a>"""
    }
}
