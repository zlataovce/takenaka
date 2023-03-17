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

package me.kcra.takenaka.core.test.mapping.ancestry

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import me.kcra.takenaka.core.compositeWorkspace
import me.kcra.takenaka.core.mapping.VersionedMappingMap
import me.kcra.takenaka.core.mapping.ancestry.classAncestryTreeOf
import me.kcra.takenaka.core.test.mapping.resolve.resolveMappings
import me.kcra.takenaka.core.util.objectMapper
import kotlin.test.BeforeTest
import kotlin.test.Test

class ClassAncestryTest {
    private val objectMapper = objectMapper()
    private val xmlMapper = XmlMapper()

    lateinit var mappings: VersionedMappingMap

    @BeforeTest
    fun `resolve mappings`() {
        val workspace = compositeWorkspace {
            rootDirectory("test-workspace")

            resolverOptions {
                relaxedCache()
            }
        }

        mappings = workspace.resolveMappings(listOf("1.19.3", "1.18.2"), objectMapper, xmlMapper)
    }

    @Test
    fun `make an ancestry tree`() {
        classAncestryTreeOf(mappings, allowedNamespaces = listOf("mojang", "searge", "intermediary", "spigot"))
    }
}
