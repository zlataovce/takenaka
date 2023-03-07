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

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.*
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.VersionedMappingMap
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.buildMappingTree
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.versionManifest
import kotlin.coroutines.CoroutineContext

/**
 * A function for providing a list of mapping contributors for a single version.
 */
typealias ContributorProvider = AbstractGenerator.(VersionedWorkspace) -> List<MappingContributor>

/**
 * An abstract base for a generator.
 * A generator transforms mappings into another form, such as accessors or documentation.
 *
 * @param workspace the workspace in which this generator can move around
 * @param versions the Minecraft versions that this generator will process
 * @param coroutineDispatcher the Kotlin Coroutines context
 * @param skipSynthetics whether synthetic classes and their members should be skipped
 * @param mappingWorkspace the workspace in which the mappings are stored
 * @param contributorProvider a function that provides mapping contributors to be processed
 * @author Matouš Kučera
 */
abstract class AbstractGenerator(
    val workspace: Workspace,
    val versions: List<String>,
    val coroutineDispatcher: CoroutineContext = Dispatchers.IO,
    val skipSynthetics: Boolean = false,
    private val mappingWorkspace: CompositeWorkspace,
    private val contributorProvider: ContributorProvider
) {
    /**
     * An object mapper instance for this generator.
     */
    val objectMapper = objectMapper()

    /**
     * An XML object mapper instance for this generator.
     */
    val xmlMapper = XmlMapper()

    /**
     * The mappings.
     */
    val mappings: VersionedMappingMap by lazy(::resolveMappings)

    /**
     * Launches the generator.
     */
    abstract fun generate()

    /**
     * Resolves mappings for all targeted versions.
     *
     * @return a map of joined mapping files, keyed by version
     */
    private fun resolveMappings(): VersionedMappingMap = runBlocking(coroutineDispatcher) {
        val manifest = objectMapper.versionManifest()

        return@runBlocking versions
            .map { manifest[it] ?: error("did not find version $it in manifest") }
            .parallelMap { version ->
                val versionWorkspace by mappingWorkspace.createVersioned {
                    this.version = version
                }

                return@parallelMap version to buildMappingTree {
                    contributor(contributorProvider(versionWorkspace))

                    interceptAfter { tree ->
                        tree.filterWithModifiers()

                        if (skipSynthetics) {
                            tree.filterNonSynthetic()
                        }

                        tree.filterNonStaticInitializer()

                        tree.dstNamespaces.forEach { ns ->
                            if (ns in VanillaMappingContributor.NAMESPACES) return@forEach

                            tree.completeMethodOverrides(ns)
                        }
                    }
                }
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
