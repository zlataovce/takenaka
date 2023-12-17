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
import me.kcra.takenaka.core.mapping.matchers.isConstructor
import me.kcra.takenaka.core.mapping.resolve.AbstractMappingResolver
import me.kcra.takenaka.core.mapping.resolve.LicenseResolver
import me.kcra.takenaka.core.mapping.resolve.Output
import me.kcra.takenaka.core.mapping.resolve.lazyOutput
import me.kcra.takenaka.core.mapping.util.unwrap
import me.kcra.takenaka.core.util.*
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.tree.MappingTree
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.bufferedReader
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the Quilt mappings from QuiltMC.
 *
 * @property workspace the workspace
 * @property quiltProvider the Quilt metadata provider
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 * @author Florentin Schleuß
 */
class QuiltMappingResolver(
    override val workspace: VersionedWorkspace,
    val quiltProvider: QuiltMetadataProvider,
    val relaxedCache: Boolean = true
) : AbstractMappingResolver(), MappingContributor, LicenseResolver {
    override val licenseSource: String
        get() = "https://raw.githubusercontent.com/QuiltMC/quilt-mappings/${version.id}/LICENSE"
    override val targetNamespace: String = "quilt"
    override val outputs: List<Output<out Path?>>
        get() = listOf(mappingOutput, licenseOutput)

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param xmlMapper an [ObjectMapper] that can deserialize XML trees
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    constructor(workspace: VersionedWorkspace, xmlMapper: ObjectMapper, relaxedCache: Boolean = true)
            : this(workspace, QuiltMetadataProvider(workspace, xmlMapper), relaxedCache)

    override val mappingOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[MAPPING_JAR]

            val builds = quiltProvider.versions[version.id]
            if (builds == null) {
                logger.info { "did not find Quilt mappings for ${version.id}" }
                return@resolver null
            }

            val targetBuild = builds.maxBy(QuiltBuild::buildNumber)

            var urlString = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-mappings/$targetBuild/quilt-mappings-$targetBuild-mergedv2.jar"
            URL(urlString).httpRequest(method = "HEAD") { mergedv2 ->
                if (!mergedv2.ok) {
                    logger.info { "mergedv2 Quilt mappings JAR for ${version.id} failed to fetch, falling back to no classifier" }

                    urlString = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-mappings/$targetBuild/quilt-mappings-$targetBuild.jar"
                }
            }

            val url = URL(urlString)
            val checksumUrl = URL("$urlString.sha1")

            if (MAPPING_JAR in workspace) {
                checksumUrl.httpRequest {
                    if (it.ok) {
                        val checksum = file.getChecksum(sha1Digest)

                        if (it.readText() == checksum) {
                            logger.info { "matched checksum for cached ${version.id} Quilt mappings" }
                            return@resolver findMappingFile(file)
                        }
                    } else if (file.fileSize() == url.contentLength) {
                        logger.info { "matched same length for cached ${version.id} Quilt mappings" }
                        return@resolver findMappingFile(file)
                    }
                }

                logger.warn { "checksum/length mismatch for ${version.id} Quilt mappings cache, fetching them again" }
            }

            url.httpRequest {
                if (it.ok) {
                    it.copyTo(file)

                    logger.info { "fetched ${version.id} Quilt mappings" }
                    return@resolver findMappingFile(file)
                }

                logger.warn { "failed to fetch ${version.id} Quilt mappings, received ${it.responseCode}" }
            }

            return@resolver null
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    override val licenseOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[LICENSE]

            if (LICENSE in workspace) {
                logger.info { "found cached ${version.id} Quilt license file" }
                return@resolver file
            }

            URL(licenseSource).httpRequest {
                if (it.ok) {
                    it.copyTo(file)

                    logger.info { "fetched ${version.id} Quilt license file" }
                    return@resolver file
                } else if (it.responseCode == 404) {
                    logger.info { "did not find ${version.id} Quilt mappings license file" }
                } else {
                    logger.warn { "failed to fetch Quilt mappings license file, received ${it.responseCode}" }
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

        mappingPath?.reader()?.use { reader ->
            // Quilt has official, named and hashed namespaces
            // official is the obfuscated one
            MappingReader.read(
                reader, MappingNsRenamer(
                    visitor, mapOf(
                        "official" to MappingUtil.NS_SOURCE_FALLBACK,
                        "named" to targetNamespace
                    )
                )
            )

            val licensePath by licenseOutput

            // limit the license file to 12 lines for conciseness
            licensePath?.bufferedReader()?.use {
                visitor.visitMetadata(META_LICENSE, it.lineSequence().take(12).joinToString("\\n").replace("\t", "    "))
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
                    .filter { e -> e.name == "mappings/mappings.tiny" }
                    .findFirst()
                    .orElseThrow { RuntimeException("Could not find mapping file in zip file (Quilt mappings, ${version.id})") }

                Files.copy(it.getInputStream(entry), mappingFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        return mappingFile
    }

    companion object {
        /**
         * The file name of the cached mapping JAR.
         */
        const val MAPPING_JAR = "quilt_mappings.jar"

        /**
         * The file name of the cached mappings.
         */
        const val MAPPINGS = "quilt_mappings.tiny"

        /**
         * The file name of the cached license file.
         */
        const val LICENSE = "quilt_license.txt"

        /**
         * The license metadata key.
         */
        const val META_LICENSE = "quilt_license"

        /**
         * The license source metadata key.
         */
        const val META_LICENSE_SOURCE = "quilt_license_source"
    }
}