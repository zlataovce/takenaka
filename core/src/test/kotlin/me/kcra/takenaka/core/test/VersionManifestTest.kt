package me.kcra.takenaka.core.test

import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.SpigotVersionManifest
import me.kcra.takenaka.core.manifestObjectMapper
import me.kcra.takenaka.core.versionManifest
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VersionManifestTest {
    private val objectMapper = manifestObjectMapper()

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
