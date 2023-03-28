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
import me.kcra.takenaka.core.util.*
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.format.Tiny1Reader
import java.io.Reader
import java.net.URL
import java.nio.file.Path
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
    override val licenseSource: String = "https://raw.githubusercontent.com/FabricMC/intermediary/master/LICENSE"
    override val targetNamespace: String = "intermediary"

    private val lazyResolver = resettableLazy {
        val file = workspace[MAPPINGS]

        val url = URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/${version.id}.tiny")
        val length = url.contentLength

        if (length == (-1).toLong()) {
            logger.info { "did not find Intermediary mappings for ${version.id}" }
            return@resettableLazy null
        }

        if (MAPPINGS in workspace) {
            if (file.fileSize() == length) {
                logger.info { "matched same length for cached ${version.id} Intermediary mappings" }
                return@resettableLazy file
            }

            logger.warn { "length mismatch for ${version.id} Intermediary mapping cache, fetching them again" }
        }

        url.httpRequest {
            if (it.ok) {
                it.copyTo(file)

                logger.info { "fetched ${version.id} Intermediary mappings" }
                return@resettableLazy file
            }

            logger.warn { "failed to fetch ${version.id} Intermediary mappings, received ${it.responseCode}" }
        }

        return@resettableLazy null
    }

    private val licenseLazyResolver = resettableLazy<Path?> {
        val file = workspace[LICENSE]

        if (LICENSE in workspace) {
            logger.info { "found cached Intermediary license file" }
            return@resettableLazy file
        }

        URL(licenseSource).copyTo(file)

        logger.info { "fetched Intermediary license file" }
        return@resettableLazy file
    }

    /**
     * Creates a new mapping file reader (Tiny v1 format).
     *
     * @return the reader, null if the version doesn't have mappings released
     */
    override fun reader(): Reader? {
        return lazyResolver.resetIfNotExistsAndGet()?.reader()
    }

    /**
     * Creates a new license file reader.
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun licenseReader(): Reader? {
        return licenseLazyResolver.resetIfNotExistsAndGet()?.reader()
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        reader()?.use { reader ->
            // Intermediary has official and intermediary namespaces
            // official is the obfuscated one
            Tiny1Reader.read(reader, MappingNsRenamer(visitor, mapOf("official" to MappingUtil.NS_SOURCE_FALLBACK)))

            // limit the license file to 12 lines for conciseness
            licenseReader()?.buffered()?.use { visitor.visitMetadata(META_LICENSE, it.lineSequence().take(12).joinToString("\\n") { line -> line.replace("\t", "    ") }) }
            visitor.visitMetadata(META_LICENSE_SOURCE, licenseSource)
        }
    }

    /**
     * Warms up this contributor.
     */
    override suspend fun warmup() {
        lazyResolver.value
        licenseLazyResolver.value
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

        /**
         * The license source metadata key.
         */
        const val META_LICENSE_SOURCE = "intermediary_license_source"
    }
}