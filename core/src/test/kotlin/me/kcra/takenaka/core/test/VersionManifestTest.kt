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

package me.kcra.takenaka.core.test

import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.SpigotVersionManifest
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.versionManifest
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VersionManifestTest {
    private val objectMapper = objectMapper()

    @Test
    fun `fetch and deserialize the version manifest`() {
        val manifest = objectMapper.versionManifest()

        println(manifest)

        assertNotNull(manifest["1.12.2"], message = "did not find 1.12.2 in the manifest")
        assertNotNull(manifest["1.19"], message = "did not find 1.19 in the manifest")
    }

    @Test
    fun `fetch and deserialize the spigot version manifest`() {
        val spigotManifest = objectMapper.readValue<SpigotVersionManifest>(URL("https://hub.spigotmc.org/versions/1.19.2.json"))

        println(spigotManifest)

        assertEquals(spigotManifest.refs.keys, setOf("BuildData", "Bukkit", "CraftBukkit", "Spigot"), message = "1.19.2 refs mismatch")
    }
}
