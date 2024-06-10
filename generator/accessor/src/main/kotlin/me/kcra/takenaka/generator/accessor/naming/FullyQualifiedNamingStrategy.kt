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

package me.kcra.takenaka.generator.accessor.naming

import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.accessor.GeneratedClassType
import me.kcra.takenaka.generator.accessor.model.ClassAccessor

/**
 * A fully-qualified class name strategy.
 *
 * @author Matouš Kučera
 */
internal class FullyQualifiedNamingStrategy : SimpleNamingStrategy() {
    override fun klass(model: ClassAccessor, type: GeneratedClassType): String {
        val name = model.internalName.fromInternalName() // sanitize name

        return when (type) {
            GeneratedClassType.MAPPING -> "${name}Mapping"
            GeneratedClassType.ACCESSOR -> "${name}Accessor"
            else -> throw UnsupportedOperationException("$type is not a contextual type")
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
