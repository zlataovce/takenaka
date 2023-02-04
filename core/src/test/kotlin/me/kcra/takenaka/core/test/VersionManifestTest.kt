package me.kcra.takenaka.core.test

import me.kcra.takenaka.core.manifestObjectMapper
import me.kcra.takenaka.core.versionManifest
import kotlin.test.Test
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
}
