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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ContributorProvider
import me.kcra.takenaka.core.mapping.VersionedMappingMap
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.versionManifest
import net.fabricmc.mappingio.tree.MemoryMappingTree

/**
 * An abstract base for a generator.
 * A generator transforms mappings into another form, such as accessors or documentation.
 *
 * @param workspace the workspace in which this generator can move around
 * @param versions the Minecraft versions that this generator will process
 * @param mappingWorkspace the workspace in which the mappings are stored
 * @param contributorProvider a function that provides mapping contributors to be processed
 * @author Matouš Kučera
 */
abstract class AbstractGenerator(
    val workspace: Workspace,
    val versions: List<String>,
    private val mappingWorkspace: CompositeWorkspace,
    private val contributorProvider: ContributorProvider
) {
    /**
     * An object mapper instance for this generator.
     */
    protected val objectMapper = objectMapper()

    /**
     * The mappings.
     */
    protected val mappings: VersionedMappingMap by lazy(::resolveMappings)

    /**
     * Launches the generator.
     */
    abstract fun generate()

    /**
     * Resolves mappings for all targeted versions.
     *
     * @return a map of joined mapping files, keyed by version
     */
    private fun resolveMappings(): VersionedMappingMap = runBlocking {
        val manifest = objectMapper.versionManifest()

        return@runBlocking versions
            .map { manifest[it] ?: error("did not find version $it in manifest") }
            .parallelMap { version ->
                val versionWorkspace by mappingWorkspace.versioned {
                    this.version = version
                }

                val tree = MemoryMappingTree()
                contributorProvider(versionWorkspace, objectMapper).forEach { contributor ->
                    contributor.accept(tree)
                }

                return@parallelMap version to tree
            }
            .toMap()
    }
}

/**
 * Maps an [Iterable] in parallel.
 *
 * @param f the mapping function
 * @return the remapped list
 */
suspend fun <A, B> Iterable<A>.parallelMap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}
