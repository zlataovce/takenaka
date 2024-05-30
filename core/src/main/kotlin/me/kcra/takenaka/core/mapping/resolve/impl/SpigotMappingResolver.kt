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
import me.kcra.takenaka.core.mapping.resolve.AbstractMappingResolver
import me.kcra.takenaka.core.mapping.resolve.LicenseResolver
import me.kcra.takenaka.core.mapping.resolve.Output
import me.kcra.takenaka.core.mapping.resolve.lazyOutput
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.core.mapping.util.unwrap
import me.kcra.takenaka.core.util.XML_MAPPER
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.ok
import io.github.oshai.kotlinlogging.KotlinLogging
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.format.TsrgReader
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.commons.Remapper
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

/**
 * A base resolver for Spigot mapping files.
 *
 * @property workspace the workspace
 * @property xmlMapper an [ObjectMapper] that can deserialize XML trees
 * @property spigotProvider the Spigot manifest provider
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
abstract class AbstractSpigotMappingResolver @Deprecated(
    "Jackson will be an implementation detail in the future.",
    ReplaceWith("AbstractSpigotMappingResolver(workspace, spigotProvider, relaxedCache)")
) constructor(
    override val workspace: VersionedWorkspace,
    @Deprecated("Jackson will be an implementation detail in the future.")
    val xmlMapper: ObjectMapper,
    val spigotProvider: SpigotManifestProvider,
    val relaxedCache: Boolean = true
) : AbstractMappingResolver(), MappingContributor, LicenseResolver {
    override val licenseSource: String?
        get() = spigotProvider.manifest?.let { manifest ->
            mappingAttribute.value?.let { value ->
                "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/$value?at=${manifest.refs["BuildData"]}"
            }
        }

    override val targetNamespace: String = "spigot"
    override val outputs: List<Output<out Path?>>
        get() = listOf(mappingOutput, licenseOutput, pomOutput)

    /**
     * Creates a new resolver.
     *
     * @param workspace the workspace
     * @param spigotProvider the Spigot manifest provider
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Suppress("DEPRECATION")
    constructor(workspace: VersionedWorkspace, spigotProvider: SpigotManifestProvider, relaxedCache: Boolean = true)
            : this(workspace, XML_MAPPER, spigotProvider, relaxedCache)

    /**
     * The resolved mapping attribute.
     */
    val mappingAttribute by lazy(::resolveMappingAttribute)

    override val mappingOutput = lazyOutput<Path?> {
        resolver {
            if (!mappingAttribute.exists) {
                logger.warn { "did not find ${version.id} Spigot mappings (${mappingAttribute.name})" }
                return@resolver null
            }

            val file = workspace[mappingAttribute.value!!]

            // Spigot's stash doesn't seem to support sending Content-Length headers
            if (relaxedCache && file.isRegularFile()) {
                logger.info { "found cached ${version.id} Spigot mappings (name: ${mappingAttribute.name}, value: ${mappingAttribute.value})" }
                return@resolver file
            }

            withContext(Dispatchers.IO + CoroutineName("resolve-coro")) {
                // manifest is going to be non-null, since it's used to fetch mappingAttribute
                URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/${mappingAttribute.value}?at=${spigotProvider.manifest!!.refs["BuildData"]}").httpRequest {
                    if (it.ok) {
                        it.copyTo(file)

                        logger.info { "fetched ${version.id} Spigot mappings (name: ${mappingAttribute.name}, value: ${mappingAttribute.value})" }
                        return@httpRequest file
                    }

                    logger.warn { "failed to fetch ${version.id} Spigot mappings (name: ${mappingAttribute.name}, value: ${mappingAttribute.value}), received ${it.responseCode}" }
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

    val pomOutput = lazyOutput<Path?> {
        resolver {
            val manifest = spigotProvider.manifest
            if (manifest == null) {
                logger.warn { "did not find ${version.id} CraftBukkit POM (missing Spigot manifest)" }
                return@resolver null
            }

            val file = workspace[CRAFTBUKKIT_POM]

            if (relaxedCache && file.isRegularFile()) {
                logger.info { "found cached ${version.id} CraftBukkit pom.xml" }
                return@resolver file
            }

            withContext(Dispatchers.IO + CoroutineName("resolve-coro")) {
                URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/raw/pom.xml?at=${manifest.refs["CraftBukkit"]}").httpRequest {
                    if (it.ok) {
                        it.copyTo(file)

                        logger.info { "fetched ${version.id} CraftBukkit pom.xml" }
                        return@httpRequest file
                    }

                    logger.warn { "failed to fetch ${version.id} CraftBukkit pom.xml, received ${it.responseCode}" }
                    return@httpRequest null
                }
            }
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    /**
     * Resolves a BuildData mapping attribute.
     *
     * @return the mapping attribute
     */
    protected abstract fun resolveMappingAttribute(): MappingAttribute

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        val mappingPath by mappingOutput
        
        mappingPath?.bufferedReader()?.use { reader ->
            // skip license comment, mapping-io doesn't remove comments (it will parse them)
            if (reader.readLine().startsWith('#')) {
                TsrgReader.read(reader, MappingUtil.NS_SOURCE_FALLBACK, targetNamespace, visitor)
            } else {
                // we can't seek to the beginning, so we have to make a new reader altogether
                mappingPath?.bufferedReader()?.use { TsrgReader.read(it, MappingUtil.NS_SOURCE_FALLBACK, targetNamespace, visitor) }
            }
        }
        
        val licensePath by licenseOutput
        licensePath?.reader()?.use {
            visitor.visitMetadata(META_LICENSE, it.readText())
            visitor.visitMetadata(META_LICENSE_SOURCE, licenseSource)
        }
        
        val pomPath by pomOutput

        // prepend "v" before the NMS version to match the package name
        pomPath?.reader()?.use {
            @Suppress("DEPRECATION")
            xmlMapper.readTree(it)["properties"]["minecraft_version"].asText()?.let { v ->
                visitor.visitMetadata(META_CB_NMS_VERSION, "v$v")
            }
        }
    }

    companion object {
        /**
         * The file name of the cached license file.
         */
        const val LICENSE = "spigot_license.txt"
        
        /**
         * The CraftBukkit pom.xml file.
         */
        const val CRAFTBUKKIT_POM = "craftbukkit_pom.xml"

        /**
         * The license metadata key.
         */
        const val META_LICENSE = "spigot_license"

        /**
         * The license source metadata key.
         */
        const val META_LICENSE_SOURCE = "spigot_license_source"

        /**
         * The CraftBukkit NMS version metadata key.
         */
        const val META_CB_NMS_VERSION = "cb_nms_version"
    }

    /**
     * A BuildData mapping attribute.
     *
     * @property name the attribute name
     * @property value the attribute value
     */
    data class MappingAttribute(val name: String, val value: String?) {
        /**
         * Whether this attribute exists in the manifest, i.e. [value] is not null.
         */
        val exists: Boolean
            get() = value != null
    }
}

/**
 * Returns the CraftBukkit NMS version string from the [AbstractSpigotMappingResolver.META_CB_NMS_VERSION] metadata.
 */
inline val MappingTreeView.craftBukkitNmsVersion: String?
    get() = getMetadata(AbstractSpigotMappingResolver.META_CB_NMS_VERSION)

/**
 * A resolver for Spigot class mapping files.
 *
 * @param workspace the workspace
 * @param xmlMapper an [ObjectMapper] that can deserialize XML trees
 * @param spigotProvider the Spigot manifest provider
 * @param relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
@Suppress("DEPRECATION")
class SpigotClassMappingResolver @Deprecated(
    "Jackson will be an implementation detail in the future.",
    ReplaceWith("SpigotClassMappingResolver(workspace, spigotProvider, relaxedCache)")
) constructor(
    workspace: VersionedWorkspace,
    xmlMapper: ObjectMapper,
    spigotProvider: SpigotManifestProvider,
    relaxedCache: Boolean = true
) : AbstractSpigotMappingResolver(workspace, xmlMapper, spigotProvider, relaxedCache) {
    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     * @param xmlMapper an [ObjectMapper] that can deserialize XML trees
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Deprecated(
        "Jackson will be an implementation detail in the future.",
        ReplaceWith("SpigotClassMappingResolver(workspace, relaxedCache)")
    )
    constructor(workspace: VersionedWorkspace, objectMapper: ObjectMapper, xmlMapper: ObjectMapper, relaxedCache: Boolean = true) :
            this(workspace, xmlMapper, SpigotManifestProvider(workspace, objectMapper, relaxedCache), relaxedCache)

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    constructor(workspace: VersionedWorkspace, relaxedCache: Boolean = true) :
            this(workspace, XML_MAPPER, SpigotManifestProvider(workspace, relaxedCache), relaxedCache)

    /**
     * Creates a new resolver with a supplied metadata provider.
     *
     * @param workspace the workspace
     * @param spigotProvider the Spigot manifest provider
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    constructor(workspace: VersionedWorkspace, spigotProvider: SpigotManifestProvider, relaxedCache: Boolean = true) :
            this(workspace, XML_MAPPER, spigotProvider, relaxedCache)

    /**
     * Resolves a BuildData mapping attribute.
     *
     * @return the mapping attribute
     */
    override fun resolveMappingAttribute(): MappingAttribute {
        if (spigotProvider.isAliased) {
            logger.warn { "expected ${workspace.version.id}, got ${spigotProvider.attributes?.minecraftVersion}; aliased manifest?" }
        }

        return MappingAttribute("classMappings", spigotProvider.attributes?.classMappings)
    }

    /**
     * Visits the mappings to the supplied visitor.
     *
     * @param visitor the mapping visitor
     */
    override fun accept(visitor: MappingVisitor) {
        super.accept(AnalyzingVisitor(visitor))
    }

    /**
     * A helper class for conditionally applying implicit mappings.
     *
     * @param next the mapping visitor to forward to
     */
    private class AnalyzingVisitor(next: MappingVisitor) : ForwardingMappingVisitor(next) {
        /**
         * Whether the visited destination mappings had no package names,
         * i.e. expected to be completed with [me.kcra.takenaka.core.mapping.adapter.LegacySpigotMappingPrepender] or similar.
         */
        var hasImplicitPackages = false

        /**
         * Whether the `net.minecraft.server.MinecraftServer` class was mapped.
         */
        var visitedMinecraftServer = false

        override fun visitClass(srcName: String): Boolean {
            if (srcName == "net/minecraft/server/MinecraftServer") {
                visitedMinecraftServer = true
            }

            return super.visitClass(srcName)
        }

        override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
            if (targetKind == MappedElementKind.CLASS) {
                val internalName = name?.toInternalName()
                if (internalName != null && '/' !in internalName) { // no package name
                    hasImplicitPackages = true
                }
            }

            super.visitDstName(targetKind, namespace, name)
        }

        override fun visitEnd(): Boolean {
            // add implicit (net/minecraft/server/)MinecraftServer mapping, if not declared
            if (!visitedMinecraftServer) {
                if (super.visitClass("net/minecraft/server/MinecraftServer")) {
                    super.visitDstName(
                        MappedElementKind.CLASS,
                        0,
                        if (hasImplicitPackages) {
                            "MinecraftServer"
                        } else {
                            "net/minecraft/server/MinecraftServer"
                        }
                    )
                }
            }

            return super.visitEnd()
        }
    }
}

/**
 * A resolver for Spigot member mapping files.
 *
 * @param workspace the workspace
 * @param xmlMapper an [ObjectMapper] that can deserialize XML trees
 * @param spigotProvider the Spigot manifest provider
 * @param relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
@Suppress("DEPRECATION")
class SpigotMemberMappingResolver @Deprecated(
    "Jackson will be an implementation detail in the future.",
    ReplaceWith("SpigotMemberMappingResolver(workspace, spigotProvider, relaxedCache)")
) constructor(
    workspace: VersionedWorkspace,
    xmlMapper: ObjectMapper,
    spigotProvider: SpigotManifestProvider,
    relaxedCache: Boolean = true
) : AbstractSpigotMappingResolver(workspace, xmlMapper, spigotProvider, relaxedCache) {
    private var expectPrefixedClassNames = false

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     * @param xmlMapper an [ObjectMapper] that can deserialize XML trees
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Deprecated(
        "Jackson will be an implementation detail in the future.",
        ReplaceWith("SpigotMemberMappingResolver(workspace, relaxedCache)")
    )
    constructor(workspace: VersionedWorkspace, objectMapper: ObjectMapper, xmlMapper: ObjectMapper, relaxedCache: Boolean = true) :
            this(workspace, xmlMapper, SpigotManifestProvider(workspace, objectMapper, relaxedCache), relaxedCache)

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    constructor(workspace: VersionedWorkspace, relaxedCache: Boolean = true) :
            this(workspace, XML_MAPPER, SpigotManifestProvider(workspace, relaxedCache), relaxedCache)

    /**
     * Creates a new resolver with a supplied metadata provider.
     *
     * @param workspace the workspace
     * @param spigotProvider the Spigot manifest provider
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    constructor(workspace: VersionedWorkspace, spigotProvider: SpigotManifestProvider, relaxedCache: Boolean = true) :
            this(workspace, XML_MAPPER, spigotProvider, relaxedCache)

    /**
     * Resolves a BuildData mapping attribute.
     *
     * @return the mapping attribute
     */
    override fun resolveMappingAttribute(): MappingAttribute {
        return MappingAttribute("memberMappings", spigotProvider.attributes?.memberMappings)
    }

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
        val mappingPath by mappingOutput
        if (mappingPath == null) return // mappings don't exist, return

        val visitor0 = visitor.unwrap()
        if (visitor0 !is MappingTreeView) {
            throw UnsupportedOperationException("Spigot class member mappings can only be visited to a mapping tree")
        }

        val namespaceId = visitor0.getNamespaceId(targetNamespace)
        require(namespaceId != MappingTree.NULL_NAMESPACE_ID) {
            "Mapping tree has not visited ${workspace.version.id} Spigot class mappings before"
        }

        fun getPrefixedClass(name: String): MappingTreeView.ClassMappingView? =
            visitor0.getClass("net/minecraft/server/VVV/${name.substringAfterLast('/')}", namespaceId)?.also { expectPrefixedClassNames = true }

        fun getClass(name: String): MappingTreeView.ClassMappingView? =
            // perf: reorder queries based on previously read values
            if (expectPrefixedClassNames) {
                getPrefixedClass(name) // search for prefixed class names
                    ?: visitor0.getClass(name) // search for unobfuscated class names, like Main
                    ?: visitor0.getClass(name, namespaceId)
            } else {
                visitor0.getClass(name, namespaceId)
                    ?: getPrefixedClass(name) // search for prefixed class names
                    ?: visitor0.getClass(name) // search for unobfuscated class names, like Main
            }

        val srcRemapper = object : Remapper() {
            override fun map(internalName: String): String =
                getClass(internalName)?.srcName ?: internalName
        }

        while (true) {
            if (visitor.visitHeader()) {
                visitor.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, listOf(targetNamespace))
            }

            if (visitor.visitContent()) {
                mappingPath?.bufferedReader()?.forEachLine { line ->
                    if (line.startsWith('#')) {
                        return@forEachLine // skip comments
                    }

                    val columns = line.split(' ', limit = 4).toMutableList()
                    val owner = columns[0]
                    var srcName = columns[1]

                    // this is needed for mangled mappings,
                    // which have the method name and descriptor without a space between them
                    val parenthesisIndex = srcName.indexOf('(')
                    if (parenthesisIndex != -1) {
                        srcName = srcName.substring(0, parenthesisIndex)

                        val srcNameCol = columns.set(1, srcName)
                        columns.add(2, srcNameCol.substring(parenthesisIndex))
                    }

                    val ownerKlass = getClass(owner)
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

                                if (visitor.visitMethod(srcName, srcRemapper.mapDesc(desc))) {
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
}
