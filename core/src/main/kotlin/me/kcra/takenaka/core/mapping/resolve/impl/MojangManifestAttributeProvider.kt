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
import me.kcra.takenaka.core.VersionAttributes
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.util.MAPPER
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * A provider of version attributes from Mojang's v2 version manifest.
 *
 * This class is thread-safe and presumes multiple instances operate on a single workspace.
 *
 * @property workspace the workspace
 * @property objectMapper an [ObjectMapper] that can deserialize JSON data
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
class MojangManifestAttributeProvider @Deprecated(
    "Jackson will be an implementation detail in the future.",
    ReplaceWith("MojangManifestAttributeProvider(workspace, relaxedCache)")
) constructor(val workspace: VersionedWorkspace, private val objectMapper: ObjectMapper, val relaxedCache: Boolean = true) {
    /**
     * The version attributes.
     */
    val attributes: VersionAttributes by lazy(::readAttributes)

    /**
     * Creates a new attribute provider.
     *
     * @param workspace the workspace
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Suppress("DEPRECATION")
    constructor(workspace: VersionedWorkspace, relaxedCache: Boolean = true) : this(workspace, MAPPER, relaxedCache)

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): VersionAttributes {
        return workspace.withLock(WORKSPACE_LOCK) {
            val file = workspace[ATTRIBUTES]

            if (relaxedCache && ATTRIBUTES in workspace) {
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
        private val WORKSPACE_LOCK = object {}

        /**
         * The file name of the cached attributes.
         */
        const val ATTRIBUTES = "mojang_manifest_attributes.json"
    }
}

/**
 * A Mojang manifest attribute.
 *
 * @property name the attribute name
 * @property value the attribute value
 * @property checksum the attribute checksum
 */
data class ManifestAttribute(val name: String, val value: String?, val checksum: String?) {
    /**
     * Whether this attribute exists in the manifest, i.e. [value] is not null.
     */
    val exists: Boolean
        get() = value != null && checksum != null
}
