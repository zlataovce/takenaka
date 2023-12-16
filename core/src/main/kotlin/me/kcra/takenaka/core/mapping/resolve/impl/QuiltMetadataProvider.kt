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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.readTree
import mu.KotlinLogging
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * A provider of Quilt's maven-metadata.xml file.
 *
 * This class is thread-safe, but presumes that only one instance will operate on a workspace at a time.
 *
 * @property workspace the workspace
 * @property xmlMapper an [ObjectMapper] that can deserialize XML trees
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 * @author Florentin Schleuß
 */
class QuiltMetadataProvider(val workspace: Workspace, private val xmlMapper: ObjectMapper, val relaxedCache: Boolean = true) {
    /**
     * A map of versions and their builds.
     */
    val versions by lazy(::parseVersions)

    /**
     * The XML node of the maven-metadata file.
     */
    private val metadata by lazy(::readMetadata)

    /**
     * Parses Quilt version strings in the metadata file.
     *
     * @return the version metadata
     */
    private fun parseVersions(): Map<String, List<QuiltBuild>> = buildMap<String, MutableList<QuiltBuild>> {
        metadata["versioning"]["versions"]["version"].forEach { versionNode ->
            val (version, buildNumber) = versionNode.asText().split("+build.", limit = 2)
            getOrPut(version, ::mutableListOf) += QuiltBuild(version, buildNumber.toInt())
        }
    }

    /**
     * Reads the metadata file from cache, fetching it if the cache missed.
     *
     * @return the metadata XML node
     */
    private fun readMetadata(): JsonNode {
        val file = workspace[METADATA]

        if (relaxedCache && METADATA in workspace) {
            try {
                return xmlMapper.readTree(file).apply {
                    logger.info { "read cached Quilt mappings metadata" }
                }
            } catch (e: JacksonException) {
                logger.warn(e) { "failed to read cached Quilt mappings metadata, fetching it again" }
            }
        }

        URL("https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-mappings/maven-metadata.xml").copyTo(file)

        logger.info { "fetched Quilt metadata" }
        return xmlMapper.readTree(file)
    }

    companion object {
        /**
         * The file name of the cached version metadata.
         */
        const val METADATA = "quilt_metadata.xml"
    }
}

/**
 * Information about one Quilt build.
 *
 * @property version the Minecraft version that this build is for
 * @property buildNumber the Quilt build number, higher is newer
 */
data class QuiltBuild(val version: String, val buildNumber: Int) {
    override fun toString(): String {
        return "$version+build.$buildNumber"
    }
}
