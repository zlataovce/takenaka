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
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import java.io.File
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the Searge (Forge) mappings.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class SeargeMappingResolver(val workspace: VersionedWorkspace) : MappingResolver, MappingContributor {
    private val sha1Digest = MessageDigest.getInstance("SHA-1")
    override val version: Version by workspace::version
    override val targetNamespace: String = "searge"

    /**
     * Creates a new mapping file reader (SRG/TSRG format).
     *
     * @return the reader, null if the version doesn't have mappings released
     */
    override fun reader(): Reader? {
        val file = workspace[SEARGE]

        fun mappingFileReader(url: URL, checksum: String): Reader {
            if (SEARGE in workspace) {
                val fileChecksum = file.getChecksum(sha1Digest)

                if (checksum == fileChecksum) {
                    logger.info { "matched checksum for cached ${version.id} Searge mappings" }
                    return findMappingFile(file).reader()
                }

                logger.warn { "checksum mismatch for ${version.id} Searge mapping cache, fetching them again" }
            }

            URL(url.toString().removeSuffix(".sha1")).openStream().use {
                Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            logger.info { "fetched ${version.id} Searge mappings" }
            return findMappingFile(file).reader()
        }

        var conn = URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/${version.id}/mcp_config-${version.id}.zip.sha1")
            .openConnection() as HttpURLConnection

        conn.requestMethod = "GET"
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    return mappingFileReader(conn.url, conn.inputStream.reader().use(Reader::readText))
                }
            }
        } finally {
            conn.disconnect()
        }

        // let's try the second URL

        conn = URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/${version.id}/mcp-${version.id}-srg.zip.sha1")
            .openConnection() as HttpURLConnection

        conn.requestMethod = "GET"
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    return mappingFileReader(conn.url, conn.inputStream.reader().use(Reader::readText))
                }
                else -> logger.warn { "failed to fetch ${version.id} Searge mappings, didn't find a valid URL" }
            }
        } finally {
            conn.disconnect()
        }

        return null
    }

    /**
     * Creates a new license file reader.
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun licenseReader(): Reader {
        val file = workspace[SEARGE_LICENSE]

        if (SEARGE_LICENSE in workspace) {
            logger.info { "found cached Searge license file" }
            return file.reader()
        }

        URL("https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/LICENSE").openStream().use {
            Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        return file.reader()
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        // Searge has obf, srg and id namespaces
        // obf is the obfuscated one
        reader()?.let { MappingReader.read(it, MappingNsRenamer(visitor, mapOf("obf" to "source", "srg" to "searge", "id" to "searge_id"))) }
    }

    /**
     * Extracts the mapping file from the supplied zip file.
     *
     * @param file the zip file
     * @return the file
     */
    private fun findMappingFile(file: File): File {
        val mappingFile = workspace[JOINED]

        if (!mappingFile.isFile) {
            ZipFile(file).use {
                val entry = it.stream()
                    .filter { e -> e.name == "config/joined.tsrg" || e.name == "joined.srg" }
                    .findFirst()
                    .orElseThrow { RuntimeException("Could not find mapping file in zip file (Searge, ${version.id})") }

                Files.copy(it.getInputStream(entry), mappingFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        return mappingFile
    }

    companion object {
        /**
         * The file name of the cached zip file.
         */
        const val SEARGE = "searge.zip"

        /**
         * The file name of the joined server mappings.
         */
        const val JOINED = "joined.srg"

        /**
         * The file name of the cached license file.
         */
        const val SEARGE_LICENSE = "searge_license.txt"
    }
}