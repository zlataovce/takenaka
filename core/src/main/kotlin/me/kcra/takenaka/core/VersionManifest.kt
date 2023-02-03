/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023 Matouš Kučera
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

package me.kcra.takenaka.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Mojang's v2 version manifest.
 */
data class VersionManifest(
    val latest: Latest,
    val versions: List<Version>
) {
    /**
     * The latest release and snapshot versions.
     */
    data class Latest(
        /**
         * The ID of the latest release version.
         */
        val release: String,
        /**
         * The ID of the latest snapshot version.
         */
        val snapshot: String
    )

    /**
     * Finds a version by its ID (e.g. 1.14.4).
     *
     * @param id the version ID
     * @return the version, null if not found
     */
    operator fun get(id: String): Version? = versions.find { it.id == id }
}

/**
 * A version from Mojang's version manifest.
 */
data class Version(
    /**
     * The ID of this version.
     */
    val id: String,
    /**
     * The type of this version.
     */
    val type: Type,
    /**
     * The link to the <version id>.json for this version.
     */
    val url: String,
    /**
     * A timestamp in ISO 8601 format of when the version files were last updated on the manifest.
     */
    val time: Instant,
    /**
     * The release time of this version in ISO 8601 format.
     */
    val releaseTime: Instant,
    /**
     * The SHA1 hash of the version and therefore the JSON file ID.
     */
    val sha1: String,
    /**
     * If 0, the launcher warns the user about this version not being recent enough to support the latest player safety features.
     * Its value is 1 otherwise.
     */
    val complianceLevel: Int
) : Comparable<Version> {
    enum class Type {
        RELEASE, SNAPSHOT, OLD_BETA, OLD_ALPHA
    }

    /**
     * Compares the release date of this version to another version.
     *
     * @param other the other version
     * @return the comparator value, negative if less, positive if greater
     */
    override operator fun compareTo(other: Version): Int = releaseTime.compareTo(other.releaseTime)
}

/**
 * The version attributes from Mojang's v2 version manifest.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VersionAttributes(
    /**
     * The name of this version (e.g. 1.14.4).
     */
    val id: String,
    /**
     * The type of this version.
     */
    val type: Version.Type,
    /**
     * A timestamp in ISO 8601 format of when the version files were last updated on the manifest.
     */
    val time: Instant,
    /**
     * The release time of this version in ISO 8601 format.
     */
    val releaseTime: Instant,
    /**
     * The version's downloadable resources.
     */
    val downloads: Downloads

    // unrelated fields omitted for brevity
) {
    data class Downloads(
        /**
         * The client download information.
         */
        val client: DownloadItem,
        /**
         * The server download information.
         */
        val server: DownloadItem,
        /**
         * The obfuscation maps for this client version.
         */
        @JsonProperty("client_mappings")
        val clientMappings: DownloadItem?,
        /**
         * The obfuscation maps for this server version.
         */
        @JsonProperty("server_mappings")
        val serverMappings: DownloadItem?
    )

    data class DownloadItem(
        /**
         * The SHA1 of the jar.
         */
        val sha1: String,
        /**
         * The size of jar in bytes.
         */
        val size: Int,
        /**
         * The URL where the jar is hosted.
         */
        val url: String
    )
}
