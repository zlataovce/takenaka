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

package me.kcra.takenaka.core.test.util

import me.kcra.takenaka.core.util.*
import org.junit.Test
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.isRegularFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UtilTest {
    @Test
    fun `sha1Digest should return the same instance of MessageDigest in the same thread`() {
        val digest1 = sha1Digest
        val digest2 = sha1Digest
        assertEquals(digest1, digest2)
    }

    @Test
    fun `getChecksum should return the correct checksum for a file`() {
        val expectedChecksum = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
        val path = Path("src/test/resources/testFile1.txt")
        val actualChecksum = path.getChecksum(sha1Digest)
        assertEquals(expectedChecksum, actualChecksum)
    }

    @Test
    fun `HTTP response body content length should return -1 when the header is not defined`() {
        val url = URL("https://www.google.com")
        assertEquals(-1, url.contentLength)
    }

    @Test
    fun `HTTP response body content length should return content length for valid responses`() {
        val url = URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/LICENSE")
        assertNotEquals(-1, url.contentLength)
    }

    @Test
    fun `ok property should return true for 2xx status codes`() {
        val url = URL("https://www.google.com")
        url.httpRequest {
            assertTrue(it.ok)
        }
    }

    @Test
    fun `ok property should return false for non-2xx status codes`() {
        val url = URL("https://www.google.com/a")
        url.httpRequest {
            assertFalse(it.ok)
        }
    }

    @Test
    fun `HTTP response body stream can be copied to file`() {
        val url = URL("https://google.com")
        val tempFile = createTempFile()
        url.copyTo(tempFile)
        assertTrue(tempFile.isRegularFile())
    }

    @Test
    fun `HTTP response body can be read as text`() {
        val url = URL("https://google.com")
        url.httpRequest {
            assertNotEquals("", it.readText())
        }
    }
}
