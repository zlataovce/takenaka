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

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import me.kcra.takenaka.core.VersionManifest
import me.kcra.takenaka.core.mapping.MutableMappingsMap
import me.kcra.takenaka.core.mapping.adapter.MissingDescriptorFilter
import me.kcra.takenaka.core.mapping.analysis.MappingAnalyzer
import me.kcra.takenaka.core.mapping.buildMappingTree
import me.kcra.takenaka.core.mapping.resolve.OutputContainer
import me.kcra.takenaka.core.mapping.unwrap
import me.kcra.takenaka.core.versionManifestOf
import me.kcra.takenaka.generator.common.provider.MappingProvider
import net.fabricmc.mappingio.format.Tiny2Reader
import net.fabricmc.mappingio.format.Tiny2Writer
import net.fabricmc.mappingio.tree.MemoryMappingTree
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.*
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * A [MappingProvider] implementation that fetches and caches mappings.
 *
 * @property mappingConfig configuration to alter the mapping fetching and correction process
 * @property manifest the Mojang version manifest
 * @author Matouš Kučera
 */
class ResolvingMappingProvider(val mappingConfig: MappingConfiguration, val manifest: VersionManifest) : MappingProvider {
    /**
     * Constructs this provider with a new manifest.
     *
     * @property mappingConfig configuration to alter the mapping fetching and correction process
     */
    constructor(mappingConfig: MappingConfiguration) : this(mappingConfig, versionManifestOf())

    /**
     * Resolves the mappings.
     *
     * @param analyzer an analyzer which the mappings should be visited to as they are resolved
     * @return the mappings
     */
    override suspend fun get(analyzer: MappingAnalyzer?): MutableMappingsMap {
        return mappingConfig.versions
            .map { versionString ->
                mappingConfig.workspace.createVersionedWorkspace {
                    this.version = requireNotNull(manifest[versionString]) {
                        "Version $versionString was not found in manifest"
                    }
                }
            }
            .parallelMap(Dispatchers.Default + CoroutineName("resolve-coro")) { workspace ->
                val outputFile = mappingConfig.joinedOutputProvider(workspace)
                if (outputFile != null && outputFile.isRegularFile()) {
                    // load mapping tree from file
                    try {
                        return@parallelMap workspace.version to MemoryMappingTree().also { tree ->
                            withContext(Dispatchers.IO + CoroutineName("io-coro")) {
                                outputFile.reader().use { r -> Tiny2Reader.read(r, tree) }
                                logger.info { "read ${workspace.version.id} joined mapping file from ${outputFile.pathString}" }
                            }

                            if (analyzer != null) {
                                val time = measureTimeMillis { analyzer.accept(tree) }
                                logger.info { "analyzed ${workspace.version.id} mappings in ${time}ms" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "failed to read ${workspace.version.id} joined mapping file from ${outputFile.pathString}" }
                    }
                }

                // joined mapping file not found, let's make it

                val contributors = mappingConfig.contributorProvider(workspace)
                coroutineScope {
                    contributors.forEach { _contributor ->
                        val contributor = _contributor.unwrap()

                        // pre-fetch outputs asynchronously
                        if (contributor is OutputContainer<*>) {
                            contributor.forEach { output ->
                                launch(Dispatchers.Default + CoroutineName("resolve-coro")) {
                                    output.resolve()
                                }
                            }
                        }
                    }
                }

                val tree = buildMappingTree {
                    contributor(contributors)

                    interceptors += mappingConfig.interceptors
                }

                if (analyzer != null) {
                    val time = measureTimeMillis { analyzer.accept(tree) }
                    logger.info { "analyzed ${workspace.version.id} mappings in ${time}ms" }
                }

                if (outputFile != null && !outputFile.isDirectory()) {
                    withContext(Dispatchers.IO + CoroutineName("io-coro")) {
                        Tiny2Writer(outputFile.writer(), false).use { w -> tree.accept(MissingDescriptorFilter(w)) }
                        logger.info { "wrote ${workspace.version.id} joined mapping file to ${outputFile.pathString}" }
                    }
                }

                workspace.version to tree
            }
            .toMap()
    }
}

/**
 * Maps an [Iterable] in parallel.
 *
 * @param context the coroutine context
 * @param block the mapping function
 * @return the remapped list
 */
suspend fun <A, B> Iterable<A>.parallelMap(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend (A) -> B
): List<B> = coroutineScope {
    map { async(context) { block(it) } }.awaitAll()
}
