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

import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.util.*
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.ProGuardReader
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the official Mojang server mapping files ("Mojmaps").
 *
 * @property workspace the workspace
 * @property mojangProvider the Mojang manifest provider
 * @author Matouš Kučera
 */
class MojangServerMappingResolver(
    override val workspace: VersionedWorkspace,
    val mojangProvider: MojangManifestAttributeProvider
) : AbstractMappingResolver(), MappingContributor, LicenseResolver {
    override val licenseSource: String?
        get() = mojangProvider.attributes.downloads.serverMappings?.url
    override val targetNamespace: String = "mojang"
    override val outputs: List<Output<out Path?>>
        get() = listOf(mappingOutput, licenseOutput)

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     */
    constructor(workspace: VersionedWorkspace, objectMapper: ObjectMapper) :
            this(workspace, MojangManifestAttributeProvider(workspace, objectMapper))

    override val mappingOutput = lazyOutput<Path?> {
        resolver {
            if (mojangProvider.attributes.downloads.serverMappings == null) {
                logger.info { "did not find Mojang mappings for ${version.id}" }
                return@resolver null
            }

            val file = workspace[MAPPINGS]

            if (MAPPINGS in workspace) {
                val checksum = file.getChecksum(sha1Digest)

                if (mojangProvider.attributes.downloads.serverMappings?.sha1 == checksum) {
                    logger.info { "matched checksum for cached ${version.id} Mojang mappings" }
                    return@resolver file
                }

                logger.warn { "checksum mismatch for ${version.id} Mojang mapping cache, fetching them again" }
            }

            URL(mojangProvider.attributes.downloads.serverMappings?.url).httpRequest {
                if (it.ok) {
                    it.copyTo(file)

                    logger.info { "fetched ${version.id} Mojang mappings" }
                    return@resolver file
                }

                logger.info { "failed to fetch ${version.id} Mojang mappings, received ${it.responseCode}" }
            }

            return@resolver null
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    override val licenseOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[LICENSE]
            val mappingPath by mappingOutput

            mappingPath?.bufferedReader()?.use {
                val line = it.readLine()

                if (line.startsWith("# ")) {
                    file.writeText(line.drop(2))
                    return@resolver file
                }
            }

            return@resolver null
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        val mappingPath by mappingOutput

        // Mojang maps are original -> obfuscated, so we need to switch it beforehand
        mappingPath?.reader()?.use {
            ProGuardReader.read(it, targetNamespace, MappingUtil.NS_SOURCE_FALLBACK, MappingSourceNsSwitch(visitor, MappingUtil.NS_SOURCE_FALLBACK))
        }

        val licensePath by licenseOutput

        licensePath?.reader()?.use {
            visitor.visitMetadata(META_LICENSE, it.readText())
            visitor.visitMetadata(META_LICENSE_SOURCE, licenseSource)
        }
    }

    companion object {
        /**
         * The file name of the cached server mappings.
         */
        const val MAPPINGS = "mojang_mappings.txt"

        /**
         * The file name of the cached license file.
         */
        const val LICENSE = "mojang_license.txt"

        /**
         * The license metadata key.
         */
        const val META_LICENSE = "mojang_license"

        /**
         * The license source metadata key.
         */
        const val META_LICENSE_SOURCE = "mojang_license_source"
    }
}
