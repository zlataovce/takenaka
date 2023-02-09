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
import me.kcra.takenaka.core.RELAXED_CACHE
import me.kcra.takenaka.core.VersionAttributes
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.contains
import mu.KotlinLogging
import java.net.URL
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * A consumer of the version attributes from Mojang's v2 version manifest.
 *
 * @author Matouš Kučera
 */
open class MojangManifestConsumer(
    val workspace: VersionedWorkspace,
    private val objectMapper: ObjectMapper
) {
    /**
     * The version attributes.
     */
    protected val attributes: VersionAttributes by lazy(::readAttributes)

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): VersionAttributes {
        workspace.mojangManifestLock.withLock {
            val file = workspace[ATTRIBUTES]

            if (RELAXED_CACHE in workspace.resolverOptions && ATTRIBUTES in workspace) {
                try {
                    return objectMapper.readValue<VersionAttributes>(file).apply {
                        logger.info { "read cached ${workspace.version.id} attributes" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} attributes, fetching them again" }
                }
            }

            val content = URL(workspace.version.url).readText()
            file.writeText(content)

            logger.info { "fetched ${workspace.version.id} attributes" }
            return objectMapper.readValue(content)
        }
    }

    companion object {
        /**
         * The file name of the cached attributes.
         */
        const val ATTRIBUTES = "mojang_manifest_attributes.json"
    }
}