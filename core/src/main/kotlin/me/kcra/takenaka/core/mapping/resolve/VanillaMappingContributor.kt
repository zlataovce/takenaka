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
import me.kcra.takenaka.core.DefaultResolverOptions
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.contains
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.util.*
import mu.KotlinLogging
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
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
 * @author Matouš Kučera
 */
class VanillaMappingContributor(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : MojangManifestConsumer(workspace, objectMapper), MappingContributor {
    override val targetNamespace: String = MappingUtil.NS_SOURCE_FALLBACK

    /**
     * The raw classes of the server JAR.
     */
    val classes: List<ClassNode> by lazy(::readServerJar)

    /**
     * Visits the original mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        while (true) {
            if (visitor.visitHeader()) {
                visitor.visitNamespaces(targetNamespace, listOf(NS_MODIFIERS, NS_SIGNATURE, NS_SUPER, NS_INTERFACES))
            }

            if (visitor.visitContent()) {
                classes.forEach { klass ->
                    if (visitor.visitClass(klass.name)) {
                        visitor.visitDstName(MappedElementKind.CLASS, 0, klass.access.toString(10))
                        visitor.visitDstName(MappedElementKind.CLASS, 1, klass.signature)
                        visitor.visitDstName(MappedElementKind.CLASS, 2, klass.superName)
                        if (klass.interfaces.isNotEmpty()) {
                            visitor.visitDstName(MappedElementKind.CLASS, 3, klass.interfaces.joinToString(","))
                        }

                        if (visitor.visitElementContent(MappedElementKind.CLASS)) {
                            klass.fields.forEach { field ->
                                if (visitor.visitField(field.name, field.desc)) {
                                    visitor.visitDstName(MappedElementKind.FIELD, 0, field.access.toString(10))
                                    visitor.visitDstName(MappedElementKind.FIELD, 1, field.signature)
                                    visitor.visitElementContent(MappedElementKind.FIELD)
                                }
                            }

                            klass.methods.forEach { method ->
                                if (visitor.visitMethod(method.name, method.desc)) {
                                    visitor.visitDstName(MappedElementKind.METHOD, 0, method.access.toString(10))
                                    visitor.visitDstName(MappedElementKind.METHOD, 1, method.signature)
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

    /**
     * Reads the server JAR (bundled JAR in case of a bundler).
     *
     * @return the classes
     */
    private fun readServerJar(): List<ClassNode> {
        var file = fetchServerJar() ?: return emptyList()

        fun readJar(zf: ZipFile): List<ClassNode> {
            return zf.stream()
                .filter { it.name.matches(CLASS_PATTERN) && !it.isDirectory }
                .map { entry ->
                    ClassNode().also { klass ->
                        zf.getInputStream(entry).use { inputStream ->
                            // ignore any method content and debugging data
                            ClassReader(inputStream).accept(klass, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
                        }
                    }
                }
                .collect(Collectors.toList())
        }

        ZipFile(file.toFile()).use { zf ->
            if (zf.getEntry("net/minecraft/bundler/Main.class") != null) {
                file = file.resolveSibling(file.nameWithoutExtension + "-bundled.jar")

                if (DefaultResolverOptions.RELAXED_CACHE !in workspace.resolverOptions || !file.isRegularFile()) {
                    zf.getInputStream(zf.getEntry("META-INF/versions/${workspace.version.id}/server-${workspace.version.id}.jar")).use {
                        Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING)
                    }
                    logger.info { "extracted ${workspace.version.id} bundled JAR" }
                }

                return ZipFile(file.toFile()).use(::readJar)
            }

            return readJar(zf)
        }
    }

    /**
     * Fetches the server JAR from cache, fetching it if the cache missed.
     *
     * @return the file, null if an error occurred
     */
    private fun fetchServerJar(): Path? {
        val file = workspace[SERVER]

        if (SERVER in workspace) {
            val checksum = file.getChecksum(sha1Digest)

            if (attributes.downloads.server.sha1 == checksum) {
                logger.info { "matched checksum for cached ${workspace.version.id} vanilla JAR" }
                return file
            }

            logger.warn { "checksum mismatch for ${workspace.version.id} vanilla JAR cache, fetching it again" }
        }

        URL(attributes.downloads.server.url).httpRequest {
            if (it.ok) {
                it.copyTo(file)

                logger.info { "fetched ${workspace.version.id} vanilla JAR" }
                return file
            }

            logger.info { "failed to fetch ${workspace.version.id} vanilla JAR, received ${it.responseCode}" }
        }

        return null
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
