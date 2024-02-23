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

package me.kcra.takenaka.generator.common.provider.impl

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kcra.takenaka.core.VersionManifest
import me.kcra.takenaka.core.mapping.MutableMappingsMap
import me.kcra.takenaka.core.mapping.analysis.MappingAnalyzer
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.versionManifest
import me.kcra.takenaka.generator.common.provider.MappingProvider
import mu.KotlinLogging
import net.fabricmc.mappingio.format.Tiny2Reader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * A [MappingProvider] implementation that reads a mapping bundle.
 *
 * A mapping bundle is a ZIP file with `.tiny` (Tiny2-formatted) files named by the corresponding mapping version;
 * folders, sub-folders and extra files are permitted.
 *
 * @property file the bundle file
 * @property versions a version subset of the bundle to be loaded, everything is loaded if empty
 * @property manifest the Mojang version manifest
 * @author Matouš Kučera
 */
class BundledMappingProvider(val file: Path, val versions: List<String>, val manifest: VersionManifest) :
    MappingProvider {
    /**
     * Constructs this provider with a new manifest.
     *
     * @param file the bundle file
     * @param versions a version subset of the bundle to be loaded, everything is loaded if empty
     * @param objectMapper a JSON object mapper instance
     */
    constructor(file: Path, versions: List<String>, objectMapper: ObjectMapper = objectMapper())
            : this(file, versions, objectMapper.versionManifest())

    /**
     * Resolves the mappings.
     *
     * @param analyzer an analyzer which the mappings should be visited to as they are resolved
     * @return the mappings
     */
    override suspend fun get(analyzer: MappingAnalyzer?): MutableMappingsMap =
        withContext(Dispatchers.IO + CoroutineName("io-coro")) {
            ZipFile(file.toFile()).use { zf ->
                zf.entries()
                    .asSequence()
                    .mapNotNull { entry ->
                        if (entry.isDirectory || !entry.name.endsWith(".tiny")) return@mapNotNull null

                        val versionString = entry.name.substringAfterLast('/').removeSuffix(".tiny")

                        // skip this version, it's not in the targeted subset
                        if (versions.isNotEmpty() && versionString !in versions) return@mapNotNull null
                        try {
                            val version = checkNotNull(manifest[versionString]) {
                                "Version $versionString was not found in manifest"
                            }

                            return@mapNotNull version to MemoryMappingTree().apply {
                                zf.getInputStream(entry).reader().use { r -> Tiny2Reader.read(r, this) }
                                logger.info { "read ${version.id} mapping file from ${entry.name}" }

                                if (analyzer != null) {
                                    val time = measureTimeMillis { analyzer.accept(this) }
                                    logger.info { "analyzed ${version.id} mappings in ${time}ms" }
                                }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "failed to read mapping file from ${entry.name}" }
                        }

                        return@mapNotNull null
                    }
                    .toMap()
            }
        }
}
