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
import me.kcra.takenaka.core.*
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.ok
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.format.MappingFormat
import java.io.Reader
import java.net.URL
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * A base resolver for Spigot mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
abstract class AbstractSpigotMappingResolver(
    val workspace: VersionedWorkspace,
    private val objectMapper: ObjectMapper
) : MappingResolver, MappingContributor {
    override val version: Version by workspace::version
    override val targetNamespace: String = "spigot"

    /**
     * The version manifest.
     */
    val manifest by lazy(::readManifest)

    /**
     * The version attributes.
     */
    val attributes by lazy(::readAttributes)

    /**
     * The name of the attribute with the mapping file name.
     */
    abstract val mappingAttributeName: String

    /**
     * The value of the attribute with the mapping file name.
     */
    abstract val mappingAttribute: String?

    /**
     * Creates a new mapping file reader (CSRG format).
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun reader(): Reader? {
        if (mappingAttribute == null) {
            logger.warn { "did not find ${version.id} Spigot mappings ($mappingAttributeName)" }
            return null
        }

        val file = workspace[mappingAttribute!!]

        // Spigot's stash doesn't seem to support sending Content-Length headers
        if (RELAXED_CACHE in workspace.resolverOptions && file.isFile) {
            return file.reader()
        }

        URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/$mappingAttribute?at=${manifest.refs["BuildData"]}").httpRequest {
            if (it.ok) {
                it.copyTo(file.toPath())

                logger.info { "fetched ${version.id} Spigot mappings ($mappingAttribute)" }
                return file.reader()
            }

            logger.warn { "failed to fetch ${version.id} Spigot mappings ($mappingAttribute), received ${it.responseCode}" }
        }

        return null
    }

    /**
     * Creates a new license file reader.
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun licenseReader(): Reader? {
        // read first line of the mapping file
        return reader()?.buffered()?.use { it.readLine().reader() }
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        // mapping-io doesn't detect TSRG (CSRG), so we need to specify it manually
        reader()?.let { MappingReader.read(it, MappingFormat.TSRG, MappingNsRenamer(visitor, mapOf(MappingUtil.NS_TARGET_FALLBACK to "spigot"))) }
    }

    /**
     * Reads the manifest of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the manifest
     */
    private fun readManifest(): SpigotVersionManifest {
        // synchronize on the version instance, so both the class and member mapping resolvers
        // don't try to fetch the same files simultaneously
        workspace.spigotManifestLock.withLock {
            val file = workspace[MANIFEST]

            if (RELAXED_CACHE in workspace.resolverOptions && MANIFEST in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionManifest>(file).apply {
                        logger.info { "read cached ${version.id} Spigot manifest" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${version.id} Spigot manifest, fetching it again" }
                }
            }

            val content = URL("https://hub.spigotmc.org/versions/${version.id}.json").readText()
            file.writeText(content)

            logger.info { "fetched ${version.id} Spigot manifest" }
            return objectMapper.readValue(content)
        }
    }

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): SpigotVersionAttributes {
        // synchronize on the version instance, so both the class and member mapping resolvers
        // don't try to fetch the same files simultaneously
        workspace.spigotManifestLock.withLock {
            val file = workspace[BUILDDATA_INFO]

            if (RELAXED_CACHE in workspace.resolverOptions && BUILDDATA_INFO in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionAttributes>(file).apply {
                        logger.info { "read cached ${version.id} Spigot attributes" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${version.id} Spigot attributes, fetching them again" }
                }
            }

            val content = URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/info.json?at=${manifest.refs["BuildData"]}").readText()
            file.writeText(content)

            logger.info { "fetched ${version.id} Spigot attributes" }
            return objectMapper.readValue(content)
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

/**
 * A resolver for Spigot class mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class SpigotClassMappingResolver(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : AbstractSpigotMappingResolver(workspace, objectMapper) {
    override val mappingAttribute: String
        get() = attributes.classMappings
    override val mappingAttributeName: String
        get() = "classMappings"
}

/**
 * A resolver for Spigot member mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class SpigotMemberMappingResolver(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : AbstractSpigotMappingResolver(workspace, objectMapper) {
    override val mappingAttribute: String?
        get() = attributes.memberMappings
    override val mappingAttributeName: String
        get() = "memberMappings"
}
