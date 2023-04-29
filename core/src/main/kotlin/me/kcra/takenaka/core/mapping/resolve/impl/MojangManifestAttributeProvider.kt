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

package me.kcra.takenaka.core.mapping.resolve.impl

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.DefaultWorkspaceOptions
import me.kcra.takenaka.core.VersionAttributes
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.contains
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.readValue
import mu.KotlinLogging
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * A provider of version attributes from Mojang's v2 version manifest.
 *
 * This class is thread-safe and presumes multiple instances operate on a single workspace.
 *
 * @property workspace the workspace
 * @property objectMapper an [ObjectMapper] that can deserialize JSON data
 * @author Matouš Kučera
 */
class MojangManifestAttributeProvider(val workspace: VersionedWorkspace, private val objectMapper: ObjectMapper) {
    /**
     * The version attributes.
     */
    val attributes: VersionAttributes by lazy(::readAttributes)

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): VersionAttributes {
        return workspace.withLock("mojang-manifest") {
            val file = workspace[ATTRIBUTES]

            if (DefaultWorkspaceOptions.RELAXED_CACHE in workspace.options && ATTRIBUTES in workspace) {
                try {
                    return@withLock objectMapper.readValue<VersionAttributes>(file).apply {
                        logger.info { "read cached ${workspace.version.id} attributes" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} attributes, fetching them again" }
                }
            }

            URL(workspace.version.url).copyTo(file)

            logger.info { "fetched ${workspace.version.id} attributes" }
            return@withLock objectMapper.readValue(file)
        }
    }

    companion object {
        /**
         * The file name of the cached attributes.
         */
        const val ATTRIBUTES = "mojang_manifest_attributes.json"
    }
}