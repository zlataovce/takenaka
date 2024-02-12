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

package me.kcra.takenaka.core.mapping.resolve.impl

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.*
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.ok
import me.kcra.takenaka.core.util.readValue
import mu.KotlinLogging
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * A provider of Spigot's version manifest.
 *
 * This class is thread-safe and presumes multiple instances operate on a single workspace.
 *
 * @property workspace the workspace
 * @property objectMapper an [ObjectMapper] that can deserialize JSON data
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
class SpigotManifestProvider(val workspace: VersionedWorkspace, private val objectMapper: ObjectMapper, val relaxedCache: Boolean = true) {
    /**
     * The version manifest.
     */
    val manifest by lazy(::readManifest)

    /**
     * The version attributes.
     */
    val attributes by lazy(::readAttributes)

    /**
     * Reads the manifest of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the manifest
     */
    private fun readManifest(): SpigotVersionManifest? {
        return workspace.withLock("spigot-manifest") {
            val file = workspace[MANIFEST]

            if (relaxedCache && MANIFEST in workspace) {
                try {
                    return@withLock objectMapper.readValue<SpigotVersionManifest>(file).apply {
                        logger.info { "read cached ${workspace.version.id} Spigot manifest" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} Spigot manifest, fetching it again" }
                }
            }

            URL("https://hub.spigotmc.org/versions/${workspace.version.id}.json").httpRequest {
                if (it.ok) {
                    it.copyTo(file)

                    logger.info { "fetched ${workspace.version.id} Spigot manifest" }
                    return@withLock objectMapper.readValue(file)
                }

                logger.warn { "failed to fetch ${workspace.version.id} Spigot manifest, received ${it.responseCode}" }
            }

            return@withLock null
        }
    }

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): SpigotVersionAttributes? {
        if (manifest == null) return null

        return workspace.withLock("spigot-manifest") {
            val file = workspace[BUILDDATA_INFO]

            if (relaxedCache && BUILDDATA_INFO in workspace) {
                try {
                    return@withLock objectMapper.readValue<SpigotVersionAttributes>(file).apply {
                        logger.info { "read cached ${workspace.version.id} Spigot attributes" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} Spigot attributes, fetching them again" }
                }
            }

            URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/info.json?at=${manifest!!.refs["BuildData"]}").copyTo(file)

            logger.info { "fetched ${workspace.version.id} Spigot attributes" }
            return@withLock objectMapper.readValue(file)
        }
    }

    companion object {
        /**
         * The file name of the cached version manifest.
         */
        const val MANIFEST = "spigot_manifest.json"

        /**
         * The file name of the cached version attributes.
         */
        const val BUILDDATA_INFO = "spigot_builddata_info.json"
    }
}
