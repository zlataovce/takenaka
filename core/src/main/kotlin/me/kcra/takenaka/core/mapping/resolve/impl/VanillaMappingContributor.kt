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

package me.kcra.takenaka.core.mapping.resolve.impl

import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.DefaultWorkspaceOptions
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.contains
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.resolve.AbstractOutputContainer
import me.kcra.takenaka.core.mapping.resolve.Output
import me.kcra.takenaka.core.mapping.resolve.lazyOutput
import me.kcra.takenaka.core.util.*
import mu.KotlinLogging
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

private val logger = KotlinLogging.logger {}

/**
 * A mapping contributor that completes any missing names and descriptors, and visits modifiers and the generic signature as mappings.
 *
 * @property workspace the workspace
 * @property mojangProvider the Mojang manifest provider
 * @author Matouš Kučera
 */
class VanillaMappingContributor(
    val workspace: VersionedWorkspace,
    val mojangProvider: MojangManifestAttributeProvider
) : AbstractOutputContainer<Path?>(), MappingContributor {
    override val targetNamespace: String = MappingUtil.NS_SOURCE_FALLBACK
    override val outputs: List<Output<out Path?>>
        get() = listOf(serverJarOutput)

    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     */
    constructor(workspace: VersionedWorkspace, objectMapper: ObjectMapper) :
            this(workspace, MojangManifestAttributeProvider(workspace, objectMapper))

    /**
     * The vanilla server JAR output.
     */
    val serverJarOutput = lazyOutput<Path?> {
        resolver {
            val file = workspace[SERVER]

            if (SERVER in workspace) {
                val checksum = file.getChecksum(sha1Digest)

                if (mojangProvider.attributes.downloads.server.sha1 == checksum) {
                    logger.info { "matched checksum for cached ${workspace.version.id} vanilla JAR" }
                    return@resolver file
                }

                logger.warn { "checksum mismatch for ${workspace.version.id} vanilla JAR cache, fetching it again" }
            }

            URL(mojangProvider.attributes.downloads.server.url).httpRequest {
                if (it.ok) {
                    it.copyTo(file)

                    logger.info { "fetched ${workspace.version.id} vanilla JAR" }
                    return@resolver file
                }

                logger.info { "failed to fetch ${workspace.version.id} vanilla JAR, received ${it.responseCode}" }
            }

            return@resolver null
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    /**
     * Visits the original mappings to the supplied visitor.
     *
     * [NS_SUPER] is null if the superclass is `java/lang/Object`.
     * [NS_INTERFACES] is null if there are no superinterfaces.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        val serverJar by serverJarOutput

        fun read(zf: ZipFile) {
            val classVisitor = MappingClassVisitor(visitor, targetNamespace)

            zf.stream()
                .filter { it.name.matches(CLASS_PATTERN) && !it.isDirectory }
                .map { entry ->
                    zf.getInputStream(entry).use { inputStream ->
                        // ignore any method content and debugging data
                        ClassReader(inputStream).accept(classVisitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
                    }
                }
                .collect(Collectors.toList())
        }

        serverJar?.let { file ->
            ZipFile(file.toFile()).use { zf ->
                if (zf.getEntry("net/minecraft/bundler/Main.class") != null) {
                    val bundledFile = file.resolveSibling(file.nameWithoutExtension + "-bundled.jar")

                    if (DefaultWorkspaceOptions.RELAXED_CACHE !in workspace.options || !bundledFile.isRegularFile()) {
                        zf.getInputStream(zf.getEntry("META-INF/versions/${workspace.version.id}/server-${workspace.version.id}.jar")).use {
                            Files.copy(it, bundledFile, StandardCopyOption.REPLACE_EXISTING)
                        }
                        logger.info { "extracted ${workspace.version.id} bundled JAR" }
                    }

                    return ZipFile(bundledFile.toFile()).use(::read)
                }

                return read(zf)
            }
        }
    }

    companion object {
        private val CLASS_PATTERN = "net/minecraft/.*\\.class|[^/]+\\.class".toRegex()

        /**
         * The file name of the cached server JAR.
         */
        const val SERVER = "server.jar"

        /**
         * The namespace of the class modifiers.
         */
        const val NS_MODIFIERS = "modifiers"

        /**
         * The namespace of the class's generic signature.
         */
        const val NS_SIGNATURE = "signature"

        /**
         * The namespace of the class's superclass.
         */
        const val NS_SUPER = "super"

        /**
         * The namespace of the class's superinterfaces.
         */
        const val NS_INTERFACES = "interfaces"

        /**
         * All namespaces that this contributor produces.
         */
        val NAMESPACES = listOf(NS_MODIFIERS, NS_SIGNATURE, NS_SUPER, NS_INTERFACES)
    }

    /**
     * A [ClassVisitor] that visits classes, fields and methods to a [MappingVisitor].
     *
     * @property visitor the targeted mapping visitor
     * @param sourceNs the source namespace (contains the names of the visited classes)
     */
    class MappingClassVisitor(val visitor: MappingVisitor, sourceNs: String) : ClassVisitor(Opcodes.ASM9) {
        init {
            if (visitor.visitHeader()) {
                visitor.visitNamespaces(sourceNs, listOf(NS_MODIFIERS, NS_SIGNATURE, NS_SUPER, NS_INTERFACES))
            }

            visitor.visitContent()
        }

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            if (visitor.visitClass(name)) {
                visitor.visitDstName(MappedElementKind.CLASS, 0, access.toString(10))
                visitor.visitDstName(MappedElementKind.CLASS, 1, signature)

                if (superName != "java/lang/Object") {
                    visitor.visitDstName(MappedElementKind.CLASS, 2, superName)
                }
                if (!interfaces.isNullOrEmpty()) {
                    visitor.visitDstName(MappedElementKind.CLASS, 3, interfaces.joinToString(","))
                }
            }

            visitor.visitElementContent(MappedElementKind.CLASS)
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            if (visitor.visitField(name, descriptor)) {
                visitor.visitDstName(MappedElementKind.FIELD, 0, access.toString(10))
                visitor.visitDstName(MappedElementKind.FIELD, 1, signature)
                visitor.visitElementContent(MappedElementKind.FIELD)
            }

            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor? {
            if (visitor.visitMethod(name, descriptor)) {
                visitor.visitDstName(MappedElementKind.METHOD, 0, access.toString(10))
                visitor.visitDstName(MappedElementKind.METHOD, 1, signature)
                // TODO: exception mapping?
                visitor.visitElementContent(MappedElementKind.METHOD)
            }

            return null
        }
    }
}

/**
 * Gets the modifiers from the [VanillaMappingContributor.NS_MODIFIERS] namespace.
 */
inline val MappingTreeView.ElementMappingView.modifiers: Int
    get() = getName(VanillaMappingContributor.NS_MODIFIERS)?.toIntOrNull() ?: 0

/**
 * Gets the generic signature from the [VanillaMappingContributor.NS_SIGNATURE] namespace.
 */
inline val MappingTreeView.ElementMappingView.signature: String?
    get() = getName(VanillaMappingContributor.NS_SIGNATURE)

/**
 * Gets the superclass from the [VanillaMappingContributor.NS_SUPER] namespace.
 */
inline val MappingTreeView.ClassMappingView.superClass: String
    get() = getName(VanillaMappingContributor.NS_SUPER) ?: "java/lang/Object"

/**
 * Gets interfaces from the [VanillaMappingContributor.NS_INTERFACES] namespace.
 */
inline val MappingTreeView.ClassMappingView.interfaces: List<String>
    get() = getName(VanillaMappingContributor.NS_INTERFACES).parseInterfaces()

/**
 * Parses interfaces per the [VanillaMappingContributor.NS_INTERFACES] format.
 *
 * @return the interfaces, empty if there were none
 */
fun String?.parseInterfaces(): List<String> = this?.split(',') ?: emptyList()
