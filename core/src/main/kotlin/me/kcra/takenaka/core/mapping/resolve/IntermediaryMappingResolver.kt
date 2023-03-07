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

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.util.contentLength
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.ok
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import java.io.Reader
import java.net.URL
import kotlin.io.path.fileSize
import kotlin.io.path.reader

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the Intermediary mappings from FabricMC.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class IntermediaryMappingResolver(val workspace: VersionedWorkspace) : MappingResolver, MappingContributor {
    override val version: Version by workspace::version
    override val targetNamespace: String = "intermediary"

    /**
     * Creates a new mapping file reader (Tiny format).
     *
     * @return the reader, null if the version doesn't have mappings released
     */
    override fun reader(): Reader? {
        val file = workspace[MAPPINGS]

        val url = URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/${version.id}.tiny")
        val length = url.contentLength

        if (length == (-1).toLong()) {
            logger.info { "did not find Intermediary mappings for ${version.id}" }
            return null
        }

        if (MAPPINGS in workspace) {
            if (file.fileSize() == length) {
                logger.info { "matched same length for cached ${version.id} Intermediary mappings" }
                return file.reader()
            }

            logger.warn { "length mismatch for ${version.id} Intermediary mapping cache, fetching them again" }
        }

        url.httpRequest {
            if (it.ok) {
                it.copyTo(file)

                logger.info { "fetched ${version.id} Intermediary mappings" }
                return file.reader()
            }

            logger.warn { "failed to fetch ${version.id} Intermediary mappings, received ${it.responseCode}" }
        }

        return null
    }

    /**
     * Creates a new license file reader.
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun licenseReader(): Reader {
        val file = workspace[LICENSE]

        if (LICENSE in workspace) {
            logger.info { "found cached Intermediary license file" }
            return file.reader()
        }

        URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/LICENSE").copyTo(file)

        return file.reader()
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        // Intermediary has official and intermediary namespaces
        // official is the obfuscated one
        reader()?.use { MappingReader.read(it, MappingNsRenamer(visitor, mapOf("official" to MappingUtil.NS_SOURCE_FALLBACK))) }
        // limit the license file to 12 lines for conciseness
        licenseReader().buffered().use { visitor.visitMetadata(META_LICENSE, it.lineSequence().take(12).joinToString("\\n") { line -> line.replace("\t", "    ") }) }
    }

    companion object {
        /**
         * The file name of the cached mappings.
         */
        const val MAPPINGS = "intermediary_mappings.tiny"

        /**
         * The file name of the cached license file.
         */
        const val LICENSE = "intermediary_license.txt"

        /**
         * The license metadata key.
         */
        const val META_LICENSE = "intermediary_license"
    }
}