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
import me.kcra.takenaka.core.*
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.readValue
import mu.KotlinLogging
import java.net.URL
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * A provider of Spigot's BuildData manifest.
 *
 * @author Matouš Kučera
 */
class SpigotManifestProvider(val workspace: VersionedWorkspace, private val objectMapper: ObjectMapper) {
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
    private fun readManifest(): SpigotVersionManifest {
        workspace.spigotManifestLock.withLock {
            val file = workspace[MANIFEST]

            if (DefaultResolverOptions.RELAXED_CACHE in workspace.resolverOptions && MANIFEST in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionManifest>(file).apply {
                        logger.info { "read cached ${workspace.version.id} Spigot manifest" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} Spigot manifest, fetching it again" }
                }
            }

            URL("https://hub.spigotmc.org/versions/${workspace.version.id}.json").copyTo(file)

            logger.info { "fetched ${workspace.version.id} Spigot manifest" }
            return objectMapper.readValue(file)
        }
    }

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): SpigotVersionAttributes {
        workspace.spigotManifestLock.withLock {
            val file = workspace[BUILDDATA_INFO]

            if (DefaultResolverOptions.RELAXED_CACHE in workspace.resolverOptions && BUILDDATA_INFO in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionAttributes>(file).apply {
                        logger.info { "read cached ${workspace.version.id} Spigot attributes" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} Spigot attributes, fetching them again" }
                }
            }

            URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/info.json?at=${manifest.refs["BuildData"]}").copyTo(file)

            logger.info { "fetched ${workspace.version.id} Spigot attributes" }
            return objectMapper.readValue(file)
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
