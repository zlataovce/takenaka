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

package me.kcra.takenaka.core.mapping.resolve.impl

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.resolve.AbstractMappingResolver
import me.kcra.takenaka.core.mapping.resolve.Output
import me.kcra.takenaka.core.mapping.resolve.lazyOutput
import me.kcra.takenaka.core.util.contentLength
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.ok
import io.github.oshai.kotlinlogging.KotlinLogging
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.format.Tiny2Reader
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader

private val logger = KotlinLogging.logger {}

/**
 * A resolver for the Hashed mappings from QuiltMC.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 * @author Florentin Schleuß
 */
class HashedMappingResolver(override val workspace: VersionedWorkspace) : AbstractMappingResolver(), MappingContributor {
    override val targetNamespace: String = "hashed"
    override val outputs: List<Output<out Path?>>
        get() = listOf(mappingOutput)

    override val mappingOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[MAPPINGS]

            val url = URL("https://maven.quiltmc.org/repository/release/org/quiltmc/hashed/${version.id}/hashed-${version.id}.tiny")
            val length = url.contentLength

            if (length == -1L) {
                logger.info { "did not find Hashed mappings for ${version.id}" }
                return@resolver null
            }

            if (MAPPINGS in workspace) {
                if (file.fileSize() == length) {
                    logger.info { "matched same length for cached ${version.id} Hashed mappings" }
                    return@resolver file
                }

                logger.warn { "length mismatch for ${version.id} Hashed mapping cache, fetching them again" }
            }

            withContext(Dispatchers.IO + CoroutineName("resolve-coro")) {
                url.httpRequest {
                    if (it.ok) {
                        it.copyTo(file)

                        logger.info { "fetched ${version.id} Hashed mappings" }
                        return@httpRequest file
                    }

                    logger.warn { "failed to fetch ${version.id} Hashed mappings, received ${it.responseCode}" }
                    return@httpRequest null
                }
            }
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
            // Hashed has official and Hashed namespaces
            // official is the obfuscated one
            Tiny2Reader.read(reader, MappingNsRenamer(visitor, mapOf("official" to MappingUtil.NS_SOURCE_FALLBACK)))
        }
    }

    companion object {
        /**
         * The file name of the cached mappings.
         */
        const val MAPPINGS = "hashed_mappings.tiny"
    }
}