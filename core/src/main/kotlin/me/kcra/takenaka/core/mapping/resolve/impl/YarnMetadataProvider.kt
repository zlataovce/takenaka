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
 * A provider of Yarn's maven-metadata.xml file.
 *
 * This class is thread-safe, but presumes that only one instance will operate on a workspace at a time.
 *
 * @property workspace the workspace
 * @property xmlMapper an [ObjectMapper] that can deserialize XML trees
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
class YarnMetadataProvider(val workspace: Workspace, private val xmlMapper: ObjectMapper, val relaxedCache: Boolean = true) {
    /**
     * A map of versions and their builds.
     */
    val versions by lazy(::parseVersions)

    /**
     * The XML node of the maven-metadata file.
     */
    private val metadata by lazy(::readMetadata)

    /**
     * Parses Yarn version strings in the metadata file.
     *
     * @return the version metadata
     */
    private fun parseVersions(): Map<String, List<YarnBuild>> = buildMap<String, MutableList<YarnBuild>> {
        metadata["versioning"]["versions"]["version"].forEach { versionNode ->
            val buildString = versionNode.asText()
            val isNewFormat = "+build" in buildString

            val lastDotIndex = buildString.lastIndexOf('.')

            var version = buildString.substring(0, lastDotIndex)
            if (isNewFormat) version = version.removeSuffix("+build")

            val buildNumber = buildString.substring(lastDotIndex + 1, buildString.length).toInt()

            getOrPut(version, ::mutableListOf) += YarnBuild(version, buildNumber, isNewFormat)
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
                    logger.info { "read cached Yarn metadata" }
                }
            } catch (e: JacksonException) {
                logger.warn(e) { "failed to read cached Yarn metadata, fetching it again" }
            }
        }

        URL("https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml").copyTo(file)

        logger.info { "fetched Yarn metadata" }
        return xmlMapper.readTree(file)
    }

    companion object {
        /**
         * The file name of the cached version metadata.
         */
        const val METADATA = "yarn_metadata.xml"
    }
}

/**
 * Information about one Yarn build.
 *
 * @property version the Minecraft version that this build is for
 * @property buildNumber the Yarn build number, higher is newer
 * @property isNewFormat whether this build is named via the new format ("version+build.build_number" as opposed to "version.build_number")
 */
data class YarnBuild(val version: String, val buildNumber: Int, val isNewFormat: Boolean) {
    override fun toString(): String {
        if (isNewFormat) {
            return "$version+build.$buildNumber"
        }
        return "$version.$buildNumber"
    }
}
