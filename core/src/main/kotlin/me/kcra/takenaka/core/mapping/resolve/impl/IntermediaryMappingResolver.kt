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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.format.Tiny1Reader
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the Intermediary mappings from FabricMC.
 *
 * @property workspace the workspace
 * @property licenseWorkspace the workspace where the license will be stored
 * @author Matouš Kučera
 */
class IntermediaryMappingResolver(
    override val workspace: VersionedWorkspace,
    val licenseWorkspace: Workspace = workspace
) : AbstractMappingResolver(), MappingContributor, LicenseResolver {
    override val licenseSource: String = "https://raw.githubusercontent.com/FabricMC/intermediary/master/LICENSE"
    override val targetNamespace: String = "intermediary"
    override val outputs: List<Output<out Path?>>
        get() = listOf(mappingOutput, licenseOutput)

    override val mappingOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[MAPPINGS]

            val url = URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/${version.id}.tiny")
            val length = url.contentLength

            if (length == -1L) {
                logger.info { "did not find Intermediary mappings for ${version.id}" }
                return@resolver null
            }

            if (MAPPINGS in workspace) {
                if (file.fileSize() == length) {
                    logger.info { "matched same length for cached ${version.id} Intermediary mappings" }
                    return@resolver file
                }

                logger.warn { "length mismatch for ${version.id} Intermediary mapping cache, fetching them again" }
            }

            withContext(Dispatchers.IO + CoroutineName("resolve-coro")) {
                url.httpRequest {
                    if (it.ok) {
                        it.copyTo(file)

                        logger.info { "fetched ${version.id} Intermediary mappings" }
                        return@httpRequest file
                    }

                    logger.warn { "failed to fetch ${version.id} Intermediary mappings, received ${it.responseCode}" }
                    return@httpRequest null
                }
            }
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    override val licenseOutput = lazyOutput {
        resolver {
            licenseWorkspace.withLock(WORKSPACE_LOCK) {
                val file = licenseWorkspace[LICENSE]
                if (LICENSE in licenseWorkspace) {
                    logger.info { "found cached Intermediary license file" }
                    return@withLock file
                }

                URL(licenseSource).copyTo(file) // TODO: use IO context

                logger.info { "fetched Intermediary license file" }
                return@withLock file
            }
        }

        upToDateWhen(Path::isRegularFile)
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        val mappingPath by mappingOutput

        mappingPath?.reader()?.use { reader ->
            // Intermediary has official and intermediary namespaces
            // official is the obfuscated one
            Tiny1Reader.read(reader, MappingNsRenamer(visitor, mapOf("official" to MappingUtil.NS_SOURCE_FALLBACK)))

            val licensePath by licenseOutput

            // limit the license file to 12 lines for conciseness
            licensePath.bufferedReader().use {
                visitor.visitMetadata(META_LICENSE, it.lineSequence().take(12).joinToString("\\n") { line -> line.replace("\t", "    ") })
                visitor.visitMetadata(META_LICENSE_SOURCE, licenseSource)
            }
        }
    }

    companion object {
        private val WORKSPACE_LOCK = object {}

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