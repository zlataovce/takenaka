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

package me.kcra.takenaka.generator.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.*
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.MutableMappingsMap
import me.kcra.takenaka.core.mapping.adapter.MissingDescriptorFilter
import me.kcra.takenaka.core.mapping.buildMappingTree
import me.kcra.takenaka.core.mapping.resolve.OutputContainer
import me.kcra.takenaka.core.mapping.unwrap
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.versionManifest
import mu.KotlinLogging
import net.fabricmc.mappingio.format.Tiny2Reader
import net.fabricmc.mappingio.format.Tiny2Writer
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.*

private val logger = KotlinLogging.logger {}

/**
 * A function for providing a list of mapping contributors for a single version.
 */
typealias ContributorProvider = (VersionedWorkspace) -> List<MappingContributor>

/**
 * A function for providing a path from a versioned workspace.
 */
typealias WorkspacePathProvider = (VersionedWorkspace) -> Path?

/**
 * An abstract base for a generator that also fetches and transforms mappings.
 *
 * Joined mapping files are cached in a Tiny v2 format.
 *
 * @param workspace the workspace in which this generator can move around
 * @property objectMapper an object mapper instance for this generator
 * @property xmlMapper an XML object mapper instance for this generator
 * @author Matouš Kučera
 */
abstract class AbstractResolvingGenerator(
    workspace: Workspace,
    val objectMapper: ObjectMapper = objectMapper(),
    val xmlMapper: ObjectMapper = XmlMapper()
) : AbstractGenerator(workspace) {
    /**
     * The configuration for this generator.
     */
    abstract val mappingConfiguration: MappingConfiguration

    /**
     * Launches the generator.
     */
    suspend fun generate() = generate(resolveMappings())

    /**
     * Resolves mappings for all targeted versions.
     *
     * @return a map of joined mapping files, keyed by version
     */
    protected suspend fun resolveMappings(): MutableMappingsMap {
        val manifest = objectMapper.versionManifest()

        return mappingConfiguration.versions
            .map { versionString ->
                mappingConfiguration.workspace.createVersionedWorkspace {
                    this.version = manifest[versionString] ?: error("did not find version $versionString in manifest")
                }
            }
            .parallelMap(Dispatchers.Default + CoroutineName("mapping-coro")) { workspace ->
                val outputFile = mappingConfiguration.joinedOutputProvider(workspace)
                if (outputFile != null && outputFile.isRegularFile()) {
                    // load mapping tree from file
                    try {
                        return@parallelMap workspace.version to MemoryMappingTree().apply {
                            outputFile.reader().use { r -> Tiny2Reader.read(r, this) }
                            logger.info { "read ${workspace.version.id} joined mapping file from ${outputFile.pathString}" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "failed to read ${workspace.version.id} joined mapping file from ${outputFile.pathString}" }
                    }
                }

                // joined mapping file not found, let's make it

                val contributors = mappingConfiguration.contributorProvider(workspace)
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

                    interceptorsBefore += mappingConfiguration.visitorInterceptors
                    interceptorsAfter += mappingConfiguration.mapperInterceptors
                }

                if (outputFile != null && !outputFile.isDirectory()) {
                    Tiny2Writer(outputFile.writer(), false).use { w -> tree.accept(MissingDescriptorFilter(w)) }
                    logger.info { "wrote ${workspace.version.id} joined mapping file to ${outputFile.pathString}" }
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
