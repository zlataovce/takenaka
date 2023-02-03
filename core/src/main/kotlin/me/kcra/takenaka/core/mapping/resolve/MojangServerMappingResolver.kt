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

package me.kcra.takenaka.core.mapping.resolve

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionAttributes
import me.kcra.takenaka.core.VersionedWorkspace
import mu.KotlinLogging
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the official Mojang server mapping files ("Mojmaps").
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class MojangServerMappingResolver(
    val workspace: VersionedWorkspace,
    private val objectMapper: ObjectMapper
) : MappingResolver {
    private val sha1Digest = MessageDigest.getInstance("SHA-1")
    override val version: Version by workspace::version

    /**
     * The version attributes.
     */
    val attributes: VersionAttributes by lazy(::readAttributes)

    /**
     * Creates a new mapping file reader (ProGuard format).
     *
     * @return the reader, null if the version doesn't have server mappings released
     */
    override fun reader(): Reader? {
        if (attributes.downloads.serverMappings == null) {
            logger.info { "did not find Mojang mappings for ${version.id}" }
            return null
        }

        val file = workspace[SERVER_MAPPINGS]

        if (SERVER_MAPPINGS in workspace) {
            val checksum = file.getChecksum(sha1Digest)

            if (attributes.downloads.serverMappings?.sha1 == checksum) {
                logger.info { "matched checksum for cached ${version.id} Mojang mappings" }
                return file.reader()
            }

            logger.warn { "checksum mismatch for ${version.id} Mojang mapping cache, fetching them again" }
        }

        val conn = URL(attributes.downloads.serverMappings?.url)
            .openConnection() as HttpURLConnection

        conn.requestMethod = "GET"
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    conn.inputStream.use {
                        Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    logger.info { "fetched ${version.id} Mojang mappings" }
                    return file.reader()
                }

                else -> logger.info { "failed to fetch ${version.id} Mojang mappings, received ${conn.responseCode}" }
            }
        } finally {
            conn.disconnect()
        }

        return null
    }

    /**
     * Creates a new license file reader.
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun licenseReader(): Reader? {
        return reader()?.buffered()?.use { it.readLine().reader() }
    }

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): VersionAttributes {
        val file = workspace[ATTRIBUTES]

        if (ATTRIBUTES in workspace) {
            try {
                return objectMapper.readValue<VersionAttributes>(file).apply {
                    logger.info { "read cached ${version.id} attributes" }
                }
            } catch (e: JacksonException) {
                logger.warn(e) { "failed to read cached ${version.id} attributes, fetching them again" }
            }
        }

        val content = URL(version.url).readText()
        file.writeText(content)

        logger.info { "fetched ${version.id} attributes" }
        return objectMapper.readValue(content)
    }

    companion object {
        /**
         * The file name of the cached attributes.
         */
        const val ATTRIBUTES = "attributes.json"

        /**
         * The file name of the cached server mappings.
         */
        const val SERVER_MAPPINGS = "server_mappings.txt"
    }
}
