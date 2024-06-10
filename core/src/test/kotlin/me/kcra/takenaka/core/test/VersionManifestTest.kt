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

package me.kcra.takenaka.core.test

import me.kcra.takenaka.core.versionAttributesOf
import me.kcra.takenaka.core.versionManifestOf
import kotlin.test.*

class VersionManifestTest {
    @Test
    fun `version manifest can be fetched and deserialized`() {
        versionManifestOf()
    }

    @Test
    fun `version manifest can be fetched and deserialized, version list is not empty`() {
        val versionManifest = versionManifestOf()
        assertTrue(versionManifest.versions.isNotEmpty())
    }

    @Test
    fun `version can be found by id`() {
        val versionManifest = versionManifestOf()
        val version = versionManifest["1.14.4"]
        assertNotNull(version)
        assertEquals("1.14.4", version.id)
    }

    @Test
    fun `version list can be sorted by release time`() {
        val versionManifest = versionManifestOf()
        val sortedVersions = versionManifest.versions.sorted()
        assertTrue(sortedVersions.isNotEmpty())
        assertTrue(sortedVersions.first().releaseTime <= sortedVersions.last().releaseTime)
    }

    @Test
    fun `version attributes can be deserialized`() {
        val versionManifest = versionManifestOf()
        val version = versionManifest["1.14.4"]
        assertNotNull(version)

        versionAttributesOf(version)
    }

    @Test
    fun `server mappings are available for 1_14_4`() {
        val versionManifest = versionManifestOf()
        val version = versionManifest["1.14.4"]
        assertNotNull(version)
        val versionAttributes = versionAttributesOf(version)
        assertNotNull(versionAttributes.downloads.serverMappings)
    }

    @Test
    fun `server mappings are available for 1_12_2`() {
        val versionManifest = versionManifestOf()
        val version = versionManifest["1.12.2"]
        assertNotNull(version)
        val versionAttributes = versionAttributesOf(version)
        assertNull(versionAttributes.downloads.serverMappings)
    }
}
