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
import me.kcra.takenaka.core.mapping.resolve.OutputContainer
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.mapping.unwrap
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.core.versionManifest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
 * @param mappingWorkspace the workspace in which the mappings are stored
 * @param contributorProvider a function that provides mapping contributors to be processed
 * @param skipSynthetic whether synthetic classes and their members should be skipped
 * @param correctNamespaces namespaces excluded from any correction, these are artificial (non-mapping) namespaces defined in the core library by default
 * @author Matouš Kučera
 */
abstract class AbstractGenerator(
    val workspace: Workspace,
    val versions: List<String>,
    val mappingWorkspace: CompositeWorkspace,
    val contributorProvider: ContributorProvider,
    val skipSynthetic: Boolean = false,
    val correctNamespaces: List<String> = VanillaMappingContributor.NAMESPACES
) {
    /**
     * The mappings.
     */
    protected val mappings: VersionedMappingMap by lazy {
        runBlocking {
            resolveMappings()
        }
    }

    /**
     * An object mapper instance for this generator.
     */
    val objectMapper = objectMapper()

    /**
     * An XML object mapper instance for this generator.
     */
    val xmlMapper = XmlMapper()

    /**
     * Launches the generator.
     */
    abstract suspend fun generate()

    /**
     * Resolves mappings for all targeted versions.
     *
     * @return a map of joined mapping files, keyed by version
     */
    protected suspend fun resolveMappings(): VersionedMappingMap {
        val manifest = objectMapper.versionManifest()

        return versions
            .map { versionString ->
                mappingWorkspace.createVersionedWorkspace {
                    this.version = manifest[versionString] ?: error("did not find version $versionString in manifest")
                }
            }
            .parallelMap(Dispatchers.Default + CoroutineName("mapping-coro")) { workspace ->
                val contributors = contributorProvider(workspace)
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

                workspace.version to buildMappingTree {
                    contributor(contributors)

                    interceptBefore { tree ->
                        MethodArgSourceFilter(tree)
                    }

                    interceptAfter { tree ->
                        tree.removeElementsWithoutModifiers()

                        if (skipSynthetic) {
                            tree.removeSyntheticElements()
                        }

                        tree.removeStaticInitializers()
                        tree.removeObjectOverrides()

                        tree.dstNamespaces.forEach { ns ->
                            if (ns in correctNamespaces) return@forEach

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
