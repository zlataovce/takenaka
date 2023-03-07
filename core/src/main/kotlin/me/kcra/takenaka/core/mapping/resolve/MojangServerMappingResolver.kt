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

import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.util.*
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.ProGuardReader
import java.io.Reader
import java.net.URL
import kotlin.io.path.reader

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the official Mojang server mapping files ("Mojmaps").
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class MojangServerMappingResolver(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : MojangManifestConsumer(workspace, objectMapper), MappingResolver, MappingContributor {
    override val version: Version by workspace::version
    override val targetNamespace: String = "mojang"

    /**
     * Creates a new mapping file reader (ProGuard format).
     *
     * @return the reader, null if the version doesn't have server mappings released
     */
    override fun reader(): Reader? {
        if (attributes.downloads.serverMappings == null) {
            logger.info { "did not find Mojang mappings for ${version.id}" }
            return null
        }

        val file = workspace[MAPPINGS]

        if (MAPPINGS in workspace) {
            val checksum = file.getChecksum(sha1Digest)

            if (attributes.downloads.serverMappings?.sha1 == checksum) {
                logger.info { "matched checksum for cached ${version.id} Mojang mappings" }
                return file.reader()
            }

            logger.warn { "checksum mismatch for ${version.id} Mojang mapping cache, fetching them again" }
        }

        URL(attributes.downloads.serverMappings?.url).httpRequest {
            if (it.ok) {
                it.copyTo(file)

                logger.info { "fetched ${version.id} Mojang mappings" }
                return file.reader()
            }

            logger.info { "failed to fetch ${version.id} Mojang mappings, received ${it.responseCode}" }
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
        return reader()?.buffered()?.use { bufferedReader ->
            val line = bufferedReader.readLine()

            if (line.startsWith("# ")) line.drop(2).reader() else null
        }
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        // Mojang maps are original -> obfuscated, so we need to switch it beforehand
        reader()?.let { ProGuardReader.read(it, targetNamespace, MappingUtil.NS_SOURCE_FALLBACK, MappingSourceNsSwitch(visitor, MappingUtil.NS_SOURCE_FALLBACK)) }
        licenseReader()?.use { visitor.visitMetadata(META_LICENSE, it.readText()) }
    }

    companion object {
        /**
         * The file name of the cached server mappings.
         */
        const val MAPPINGS = "mojang_mappings.txt"

        /**
         * The license metadata key.
         */
        const val META_LICENSE = "mojang_license"
    }
}
