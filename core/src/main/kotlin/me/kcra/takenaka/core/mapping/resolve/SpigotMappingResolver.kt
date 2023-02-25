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

import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.RELAXED_CACHE
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.contains
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.ok
import mu.KotlinLogging
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.format.TsrgReader
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView
import java.io.Reader
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * A base resolver for Spigot mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
abstract class AbstractSpigotMappingResolver(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : SpigotManifestConsumer(workspace, objectMapper), MappingResolver, MappingContributor {
    override val version: Version by workspace::version
    override val targetNamespace: String = "spigot"

    /**
     * The name of the attribute with the mapping file name.
     */
    abstract val mappingAttributeName: String

    /**
     * The value of the attribute with the mapping file name.
     */
    abstract val mappingAttribute: String?

    /**
     * Creates a new mapping file reader (CSRG format).
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun reader(): Reader? {
        if (mappingAttribute == null) {
            logger.warn { "did not find ${version.id} Spigot mappings ($mappingAttributeName)" }
            return null
        }

        val file = workspace[mappingAttribute!!]

        // Spigot's stash doesn't seem to support sending Content-Length headers
        if (RELAXED_CACHE in workspace.resolverOptions && file.isFile) {
            return file.reader()
        }

        URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/$mappingAttribute?at=${manifest.refs["BuildData"]}").httpRequest {
            if (it.ok) {
                it.copyTo(file.toPath())

                logger.info { "fetched ${version.id} Spigot mappings ($mappingAttribute)" }
                return file.reader()
            }

            logger.warn { "failed to fetch ${version.id} Spigot mappings ($mappingAttribute), received ${it.responseCode}" }
        }

        return null
    }

    /**
     * Creates a new license file reader.
     *
     * @return the reader, null if this resolver doesn't support the version
     */
    override fun licenseReader(): Reader? {
        // read first line of the mapping file
        return reader()?.buffered()?.use { it.readLine().removePrefix("# ").reader() }
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        reader()?.buffered()?.let {
            // mapping-io doesn't remove comments (it will parse them)
            it.mark(0)
            if (!it.readLine().startsWith('#')) {
                it.reset()
            }

            TsrgReader.read(it, MappingUtil.NS_SOURCE_FALLBACK, targetNamespace, visitor)
        }
        licenseReader()?.use { visitor.visitMetadata(META_LICENSE, it.readText()) }
    }

    companion object {
        /**
         * The license metadata key.
         */
        const val META_LICENSE = "spigot_license"
    }
}

/**
 * A resolver for Spigot class mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class SpigotClassMappingResolver(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : AbstractSpigotMappingResolver(workspace, objectMapper) {
    override val mappingAttributeName: String = "classMappings"
    override val mappingAttribute: String? = attributes.classMappings
}

/**
 * A resolver for Spigot member mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class SpigotMemberMappingResolver(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : AbstractSpigotMappingResolver(workspace, objectMapper) {
    override val mappingAttributeName: String = "memberMappings"
    override val mappingAttribute: String? = attributes.memberMappings

    /**
     * Visits the mappings to the supplied visitor.
     *
     * A [MappingTreeView] (or naturally, [MappingTree]) instance must be passed into this resolver,
     * because it relies on the visitation of a preceding [SpigotClassMappingResolver] for matching class names.
     *
     * The mapping tree instance can have one or more [ForwardingMappingVisitor]s in front of it.
     *
     * @param visitor the mapping tree
     */
    override fun accept(visitor: MappingVisitor) {
        var visitor0 = visitor

        // skip over forwarding visitors in search of a mapping tree
        while (visitor0 is ForwardingMappingVisitor) visitor0 = visitor0.next
        if (visitor0 !is MappingTreeView) {
            throw UnsupportedOperationException("Spigot class member mappings can only be visited to a mapping tree")
        }

        val namespaceId = visitor0.getNamespaceId(targetNamespace)
        if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
            error("Mapping tree has not visited Spigot class mappings before")
        }

        while (true) {
            if (visitor.visitHeader()) {
                visitor.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, listOf(targetNamespace))
            }

            if (visitor.visitContent()) {
                reader()?.buffered()?.forEachLine { line ->
                    if (line.startsWith('#')) {
                        return@forEachLine // skip comments
                    }

                    val columns = line.split(' ', limit = 4)
                    val owner = columns[0]
                    val srcName = columns[1]

                    val ownerKlass = visitor0.getClass(owner, namespaceId)
                        ?: visitor0.getClass("net/minecraft/server/VVV/${owner.substringAfterLast('/')}", namespaceId) // search for prefixed class names
                        ?: visitor0.getClass(owner) // search for unobfuscated class names, like Main and MinecraftServer

                    if (ownerKlass == null) {
                        logger.warn { "skipping member $srcName in $owner, unknown owner" }
                        return@forEachLine
                    }

                    if (visitor.visitClass(ownerKlass.srcName) && visitor.visitElementContent(MappedElementKind.CLASS)) {
                        when (columns.size) {
                            3 -> { // field
                                val name = columns[2]

                                if (visitor.visitField(srcName, null)) {
                                    visitor.visitDstName(MappedElementKind.FIELD, 0, name)
                                    visitor.visitElementContent(MappedElementKind.FIELD)
                                }
                            }
                            4 -> { // method
                                val desc = columns[2]
                                val name = columns[3]

                                if (visitor.visitMethod(srcName, null)) {
                                    visitor.visitDstName(MappedElementKind.METHOD, 0, name)
                                    visitor.visitDstDesc(MappedElementKind.METHOD, 0, desc)
                                    visitor.visitElementContent(MappedElementKind.METHOD)
                                }
                            }
                        }
                    }
                }
            }

            if (visitor.visitEnd()) {
                break
            }
        }
    }

    companion object {
        // HACK!
        private val NEXT_VISITOR_FIELD = ForwardingMappingVisitor::class.java.getDeclaredField("next").apply { isAccessible = true }

        /**
         * Gets the delegate visitor.
         */
        val ForwardingMappingVisitor.next: MappingVisitor
            get() = NEXT_VISITOR_FIELD.get(this) as MappingVisitor
    }
}
