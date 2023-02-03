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

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionedWorkspace
import mu.KotlinLogging
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the Intermediary mappings from FabricMC.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class IntermediaryMappingResolver(val workspace: VersionedWorkspace) : MappingResolver {
    override val version: Version by workspace::version

    /**
     * Creates a new mapping file reader (Tiny format).
     *
     * @return the reader, null if the version doesn't have mappings released
     */
    override fun reader(): Reader? {
        val file = workspace[INTERMEDIARY]

        val conn = URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/${version.id}.tiny")
            .openConnection() as HttpURLConnection

        conn.requestMethod = "HEAD"
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    if (INTERMEDIARY in workspace) {
                        if (file.length() == conn.contentLengthLong) {
                            logger.info { "matched same length for cached ${version.id} Intermediary mappings" }
                            return file.reader()
                        }

                        logger.warn { "length mismatch for ${version.id} Intermediary mapping cache, fetching them again" }
                    }
                }

                HttpURLConnection.HTTP_NOT_FOUND -> {
                    logger.info { "failed to fetch ${version.id} Intermediary mappings, version not found" }
                    return null
                }
            }
        } finally {
            conn.disconnect()
        }

        conn.requestMethod = "GET"
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    conn.inputStream.use {
                        Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    logger.info { "fetched ${version.id} Intermediary mappings" }
                    return file.reader()
                }

                else -> logger.warn { "failed to fetch ${version.id} Intermediary mappings, received ${conn.responseCode}" }
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
    override fun licenseReader(): Reader {
        val file = workspace[INTERMEDIARY_LICENSE]

        if (INTERMEDIARY_LICENSE in workspace) {
            logger.info { "found cached Intermediary license file" }
            return file.reader()
        }

        URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/LICENSE").openStream().use {
            Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        return file.reader()
    }

    companion object {
        /**
         * The file name of the cached mappings.
         */
        const val INTERMEDIARY = "intermediary.tiny"

        /**
         * The file name of the cached license file.
         */
        const val INTERMEDIARY_LICENSE = "intermediary_license.txt"
    }
}