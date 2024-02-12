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

package me.kcra.takenaka.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.util.*
import java.io.IOException
import java.io.Serializable
import java.net.URL
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile

/**
 * The URL to the v2 version manifest.
 */
const val VERSION_MANIFEST_V2 = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

/**
 * Fetches and deserializes the version manifest from Mojang's API.
 *
 * @return the version manifest
 */
fun ObjectMapper.versionManifest(): VersionManifest = readValue(URL(VERSION_MANIFEST_V2))

/**
 * Retrieves the version manifest from Mojang's API or a cache file,
 * fetching it if it could not be deserialized or the content length changed.
 *
 * @param cacheFile the cache file, does not need to exist
 * @return the version manifest
 */
fun ObjectMapper.cachedVersionManifest(cacheFile: Path): VersionManifest {
    val url = URL(VERSION_MANIFEST_V2)

    if (cacheFile.isRegularFile()) {
        val length = url.contentLength
        if (cacheFile.fileSize() == length) {
            try {
                return readValue(cacheFile)
            } catch (_: JacksonException) {
                // failed to read cached file, corrupted? fetch it again
            }
        }
    }

    url.httpRequest {
        if (it.ok) {
            cacheFile.parent.createDirectories()
            it.copyTo(cacheFile)

            return readValue(cacheFile)
        }

        throw IOException("Failed to fetch v2 Mojang manifest, received ${it.responseCode}")
    }
}

/**
 * Mojang's v2 version manifest.
 *
 * @property latest the latest versions
 * @property versions all versions, sorting is maintained when deserialized (descending in case of [VERSION_MANIFEST_V2])
 */
data class VersionManifest(
    val latest: Latest,
    val versions: List<Version>
) : Serializable {
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
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Finds a version by its ID (e.g. 1.14.4).
     *
     * @param id the version ID
     * @return the version, null if not found
     */
    operator fun get(id: String): Version? = versions.find { it.id == id }

    companion object {
        private const val serialVersionUID = 1L
    }
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
) : Comparable<Version>, Serializable {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Version

        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()

    companion object {
        private const val serialVersionUID = 1L
    }
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
    @JsonIgnoreProperties(ignoreUnknown = true) // apparently there are more fields for old versions
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

/**
 * A builder for a range of Minecraft versions.
 *
 * @param manifest the version manifest
 * @param older the older version bound (inclusive), defaults to the oldest if null
 * @param newer the newer version bound (inclusive), defaults to the newest if null
 */
class VersionRangeBuilder(manifest: VersionManifest, older: String? = null, newer: String? = null) {
    /**
     * The current versions.
     */
    private val versions: MutableList<Version>

    init {
        val olderIndex = older?.let { o -> manifest.versions.indexOfFirst { it.id == o } } ?: manifest.versions.lastIndex
        require(olderIndex != -1) {
            "Version $older not found in manifest"
        }

        val newerIndex = newer?.let { n -> manifest.versions.indexOfFirst { it.id == n } } ?: 0
        require(newerIndex != -1) {
            "Version $newer not found in manifest"
        }

        this.versions = manifest.versions.subList(newerIndex, olderIndex + 1).toMutableList()
    }

    /**
     * Excludes versions whose ID is contained in [versions].
     *
     * @param versions the versions to be excluded
     */
    fun exclude(vararg versions: String) {
        this.versions.removeIf { it.id in versions }
    }

    /**
     * Excludes versions whose type is not contained in [types].
     *
     * @param types the version types to be included
     */
    fun includeTypes(vararg types: Version.Type) {
        this.versions.retainAll { it.type in types }
    }

    /**
     * Excludes versions whose type name **is not** contained in upper-cased [types].
     *
     * @param types the version type names to be included
     */
    fun includeTypes(vararg types: String) {
        val upperCaseTypes = types.map(String::uppercase)

        this.versions.retainAll { it.type.name in upperCaseTypes }
    }

    /**
     * Excludes versions whose type **is** contained in [types].
     *
     * @param types the version types to be excluded
     */
    fun excludeTypes(vararg types: Version.Type) {
        this.versions.removeIf { it.type in types }
    }

    /**
     * Excludes versions whose type name **is** contained in upper-cased [types].
     *
     * @param types the version type names to be excluded
     */
    fun excludeTypes(vararg types: String) {
        val upperCaseTypes = types.map(String::uppercase)

        this.versions.removeIf { it.type.name in upperCaseTypes }
    }

    /**
     * Returns the versions which matched the builder criteria.
     *
     * @return the versions
     */
    fun toVersionList(): List<Version> = versions
}

/**
 * Builds a version range.
 *
 * @param older the older version bound (inclusive), defaults to the oldest if null
 * @param newer the newer version bound (inclusive), defaults to the newest if null
 * @param block the range configurer, includes all releases by default
 */
inline fun VersionManifest.range(
    older: String? = null,
    newer: String? = null,
    block: VersionRangeBuilder.() -> Unit = { includeTypes(Version.Type.RELEASE) }
): List<Version> {
    return VersionRangeBuilder(this, older, newer).apply(block).toVersionList()
}

/**
 * Spigot's version manifest.
 *
 * @property refs the Git ref hashes (BuildData, Bukkit, CraftBukkit, Spigot)
 */
data class SpigotVersionManifest(
    val name: String,
    val description: String,
    val refs: Map<String, String>,
    val toolsVersion: Int,
    val javaVersions: List<Int>?
)

/**
 * Spigot's BuildData info.json attributes.
 */
data class SpigotVersionAttributes(
    val minecraftVersion: String,
    val spigotVersion: String?,
    val serverUrl: String?,
    val minecraftHash: String?,
    val accessTransforms: String,
    val mappingsUrl: String?,
    val classMappings: String?,
    val memberMappings: String?,
    val packageMappings: String?,
    val classMapCommand: String?,
    val memberMapCommand: String?,
    val finalMapCommand: String?,
    val decompileCommand: String?,
    val toolsVersion: Int?
)
