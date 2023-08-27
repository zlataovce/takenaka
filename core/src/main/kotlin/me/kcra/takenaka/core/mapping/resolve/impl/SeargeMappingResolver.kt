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

import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.util.*
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import java.io.Reader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the Searge (Forge) mappings.
 *
 * @property workspace the workspace
 * @property licenseWorkspace the workspace where the license will be stored
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
class SeargeMappingResolver(
    override val workspace: VersionedWorkspace,
    val licenseWorkspace: Workspace = workspace,
    val relaxedCache: Boolean = true
) : AbstractMappingResolver(), MappingContributor, LicenseResolver {
    override val licenseSource: String = "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/LICENSE"
    override val targetNamespace: String = "searge"
    override val outputs: List<Output<out Path?>>
        get() = listOf(mappingOutput, licenseOutput)

    override val mappingOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[MCP_CONFIG]
            var url = URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/${version.id}/mcp_config-${version.id}.zip.sha1")

            fun readMcpConfig(checksum: String): Path {
                if (MCP_CONFIG in workspace) {
                    val fileChecksum = file.getChecksum(sha1Digest)

                    if (checksum == fileChecksum) {
                        logger.info { "matched checksum for cached ${version.id} Searge mappings" }
                        return findMappingFile(file)
                    }

                    logger.warn { "checksum mismatch for ${version.id} Searge mapping cache, fetching them again" }
                }

                URL(url.toString().removeSuffix(".sha1")).copyTo(file)

                logger.info { "fetched ${version.id} Searge mappings" }
                return findMappingFile(file)
            }

            url.httpRequest {
                if (it.ok) {
                    return@resolver readMcpConfig(it.inputStream.reader().use(Reader::readText))
                }
            }

            // let's try the second URL

            url = URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/${version.id}/mcp-${version.id}-srg.zip.sha1")
            url.httpRequest {
                if (it.ok) {
                    return@resolver readMcpConfig(it.inputStream.reader().use(Reader::readText))
                }
            }

            logger.warn { "failed to fetch ${version.id} Searge mappings, didn't find a valid URL" }
            return@resolver null
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    override val licenseOutput = lazyOutput {
        resolver {
            licenseWorkspace.withLock("searge-license") {
                val file = licenseWorkspace[LICENSE]

                if (LICENSE in licenseWorkspace) {
                    logger.info { "found cached Searge license file" }
                    return@withLock file
                }

                URL(licenseSource).copyTo(file)

                logger.info { "fetched Searge license file" }
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
            // Searge has obf, srg and id namespaces; obf is the obfuscated one
            MappingReader.read(reader, MappingNsRenamer(visitor, mapOf(
                "obf" to MappingUtil.NS_SOURCE_FALLBACK,
                "srg" to "searge",
                "id" to "searge_id",
                // in older versions, there weren't any namespaces, so make sure to rename the fallback too
                MappingUtil.NS_TARGET_FALLBACK to "searge"
            )))

            val licensePath by licenseOutput

            licensePath.reader().use {
                visitor.visitMetadata(META_LICENSE, it.readLines().joinToString("\\n").replace("\t", "    "))
                visitor.visitMetadata(META_LICENSE_SOURCE, licenseSource)
            }
        }
    }

    /**
     * Extracts the mapping file from the supplied zip file.
     *
     * @param file the zip file
     * @return the file
     */
    private fun findMappingFile(file: Path): Path {
        val mappingFile = workspace[MAPPINGS]

        if (!relaxedCache || !mappingFile.isRegularFile()) {
            ZipFile(file.toFile()).use {
                val entry = it.stream()
                    .filter { e -> e.name == "config/joined.tsrg" || e.name == "joined.srg" }
                    .findFirst()
                    .orElseThrow { RuntimeException("Could not find mapping file in zip file (Searge, ${version.id})") }

                Files.copy(it.getInputStream(entry), mappingFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        return mappingFile
    }

    companion object {
        /**
         * The file name of the cached zip file.
         */
        const val MCP_CONFIG = "mcp_config.zip"

        /**
         * The file name of the server mappings.
         */
        const val MAPPINGS = "searge_mappings.srg"

        /**
         * The file name of the cached license file.
         */
        const val LICENSE = "searge_license.txt"

        /**
         * The license metadata key.
         */
        const val META_LICENSE = "searge_license"

        /**
         * The license source metadata key.
         */
        const val META_LICENSE_SOURCE = "searge_license_source"
    }
}