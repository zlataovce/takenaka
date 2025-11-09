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

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import me.kcra.takenaka.core.mapping.util.unwrap
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.ProGuardReader
import net.fabricmc.mappingio.tree.MappingTree
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

/**
 * A base resolver for the official Mojang mapping files ("Mojmaps").
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
abstract class AbstractMojangMappingResolver(
    override val workspace: VersionedWorkspace
) : AbstractMappingResolver(), MappingContributor, LicenseResolver {
    override val licenseSource: String?
        get() = mappingAttribute.value
    override val targetNamespace: String = "mojang"
    override val outputs: List<Output<out Path?>>
        get() = listOf(mappingOutput, licenseOutput)

    /**
     * The resolved mapping attribute.
     */
    val mappingAttribute by lazy(::resolveMappingAttribute)

    override val mappingOutput = lazyOutput<Path?> {
        resolver {
            if (!mappingAttribute.exists) {
                logger.info { "did not find Mojang mappings for ${version.id} (${mappingAttribute.name})" }
                return@resolver null
            }

            val fileName = "${mappingAttribute.name}.txt"
            val file = workspace[fileName]

            withContext(Dispatchers.IO + CoroutineName("resolve-coro")) {
                if (fileName in workspace) {
                    val checksum = file.getChecksum(sha1Digest)

                    if (mappingAttribute.checksum == checksum) {
                        logger.info { "matched checksum for cached ${version.id} Mojang mappings (name: ${mappingAttribute.name}, value: ${mappingAttribute.value})" }
                        return@withContext file
                    }

                    logger.warn { "checksum mismatch for ${version.id} Mojang mapping cache, fetching them again (name: ${mappingAttribute.name}, value: ${mappingAttribute.value})" }
                }

                URL(mappingAttribute.value!!).httpRequest {
                    if (it.ok) {
                        it.copyTo(file)

                        logger.info { "fetched ${version.id} Mojang mappings (name: ${mappingAttribute.name}, value: ${mappingAttribute.value})" }
                        return@httpRequest file
                    }

                    logger.info { "failed to fetch ${version.id} Mojang mappings (name: ${mappingAttribute.name}, value: ${mappingAttribute.value}), received ${it.responseCode}" }
                    return@httpRequest null
                }
            }
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    override val licenseOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[LICENSE]
            val mappingPath by mappingOutput

            withContext(Dispatchers.IO + CoroutineName("resolve-coro")) {
                mappingPath?.bufferedReader()?.use {
                    val line = it.readLine()

                    if (line.startsWith("# ")) {
                        file.writeText(line.drop(2))
                        return@withContext file
                    }
                }

                return@withContext null
            }
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    /**
     * Resolves a Mojang manifest mapping attribute.
     *
     * @return the mapping attribute
     */
    protected abstract fun resolveMappingAttribute(): ManifestAttribute

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        val visitor0 = visitor.unwrap()
        val mappingPath by mappingOutput

        val isUnobfuscated = workspace.version.maybeUnobfuscated && mappingPath == null
        if (isUnobfuscated && visitor0 is MappingTree) {
            visitor0.visitNamespaces(visitor0.srcNamespace, visitor0.dstNamespaces + targetNamespace)

            // copy everything verbatim to the "mojang" namespace
            val nsId = visitor0.getNamespaceId(targetNamespace)
            visitor0.classes.forEach { klass ->
                klass.setDstName(klass.srcName, nsId)
                klass.fields.forEach { field ->
                    field.setDstName(field.srcName, nsId)
                }
                klass.methods.forEach { method ->
                    method.setDstName(method.srcName, nsId)
                    method.args.forEach { arg ->
                        arg.setDstName(arg.srcName, nsId)
                    }
                }
            }
        } else {
            // Mojang maps are original -> obfuscated, so we need to switch it beforehand
            mappingPath?.reader()?.use {
                ProGuardReader.read(it, targetNamespace, MappingUtil.NS_SOURCE_FALLBACK, MappingSourceNsSwitch(visitor, MappingUtil.NS_SOURCE_FALLBACK))
            }
        }

        val licensePath by licenseOutput
        licensePath?.reader()?.use {
            visitor.visitMetadata(META_LICENSE, it.readText())
            visitor.visitMetadata(META_LICENSE_SOURCE, licenseSource)
        }
    }

    companion object {
        /**
         * The file name of the cached license file.
         */
        const val LICENSE = "mojang_license.txt"

        /**
         * The license metadata key.
         */
        const val META_LICENSE = "mojang_license"

        /**
         * The license source metadata key.
         */
        const val META_LICENSE_SOURCE = "mojang_license_source"
    }
}

/**
 * A resolver for Mojang client mapping files.
 *
 * @param workspace the workspace
 * @property mojangProvider the Mojang manifest provider
 * @author Matouš Kučera
 */
class MojangClientMappingResolver(
    workspace: VersionedWorkspace,
    val mojangProvider: MojangManifestAttributeProvider
) : AbstractMojangMappingResolver(workspace) {
    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Deprecated(
        "Jackson will be an implementation detail in the future.",
        ReplaceWith("MojangClientMappingResolver(workspace, relaxedCache)")
    )
    @Suppress("DEPRECATION")
    constructor(workspace: VersionedWorkspace, objectMapper: ObjectMapper, relaxedCache: Boolean = true) :
            this(workspace, MojangManifestAttributeProvider(workspace, objectMapper, relaxedCache))

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    constructor(workspace: VersionedWorkspace, relaxedCache: Boolean = true) :
            this(workspace, MojangManifestAttributeProvider(workspace, relaxedCache))

    /**
     * Resolves a Mojang manifest mapping attribute.
     *
     * @return the mapping attribute
     */
    override fun resolveMappingAttribute(): ManifestAttribute {
        return ManifestAttribute(
            "clientMappings",
            mojangProvider.attributes.downloads.clientMappings?.url,
            mojangProvider.attributes.downloads.clientMappings?.sha1
        )
    }
}

/**
 * A resolver for Mojang server mapping files.
 *
 * @param workspace the workspace
 * @property mojangProvider the Mojang manifest provider
 * @author Matouš Kučera
 */
class MojangServerMappingResolver(
    workspace: VersionedWorkspace,
    val mojangProvider: MojangManifestAttributeProvider
) : AbstractMojangMappingResolver(workspace) {
    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Deprecated(
        "Jackson will be an implementation detail in the future.",
        ReplaceWith("MojangServerMappingResolver(workspace, relaxedCache)")
    )
    @Suppress("DEPRECATION")
    constructor(workspace: VersionedWorkspace, objectMapper: ObjectMapper, relaxedCache: Boolean = true) :
            this(workspace, MojangManifestAttributeProvider(workspace, objectMapper, relaxedCache))

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    constructor(workspace: VersionedWorkspace, relaxedCache: Boolean = true) :
            this(workspace, MojangManifestAttributeProvider(workspace, relaxedCache))

    /**
     * Resolves a Mojang manifest mapping attribute.
     *
     * @return the mapping attribute
     */
    override fun resolveMappingAttribute(): ManifestAttribute {
        return ManifestAttribute(
            "serverMappings",
            mojangProvider.attributes.downloads.serverMappings?.url,
            mojangProvider.attributes.downloads.serverMappings?.sha1
        )
    }
}
