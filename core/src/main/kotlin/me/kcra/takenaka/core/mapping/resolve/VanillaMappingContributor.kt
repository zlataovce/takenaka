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
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.util.copyTo
import me.kcra.takenaka.core.util.getChecksum
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.ok
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.stream.Collectors
import java.util.zip.ZipFile

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
    private val sha1Digest = MessageDigest.getInstance("SHA-1")
    private val classPattern = "net/minecraft/.*\\.class|[^/]+\\.class".toRegex()
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
        TODO("Not yet implemented")
    }

    /**
     * Reads the server JAR (bundled JAR in case of a bundler).
     *
     * @return the classes
     */
    private fun readServerJar(): List<ClassNode> {
        var file = fetchServerJar() ?: return listOf()

        fun readJar(zf: ZipFile): List<ClassNode> {
            return zf.stream()
                .filter { it.name.matches(classPattern) && !it.isDirectory }
                .map { entry ->
                    ClassNode().also { klass ->
                        zf.getInputStream(entry).use { inputStream ->
                            // ignore any method content and debugging data
                            ClassReader(inputStream).accept(klass, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
                        }
                    }
                }
                .filter { k -> (k.access and Opcodes.ACC_SYNTHETIC) == 0 && k.innerClasses.none { k.name == it.name && it.innerName == null } } // no synthetics and no anonymous classes
                .collect(Collectors.toList())
        }

        ZipFile(file).use { zf ->
            if (zf.getEntry("net/minecraft/bundler/Main.class") != null) {
                file = file.resolveSibling(file.nameWithoutExtension + "-bundled.jar")

                if (!file.isFile) {
                    zf.getInputStream(zf.getEntry("versions/${workspace.version.id}/server-${workspace.version.id}.jar")).use {
                        Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    logger.info { "extracted ${workspace.version.id} bundled JAR" }
                }

                return ZipFile(file).use(::readJar)
            }

            return readJar(zf)
        }
    }

    /**
     * Fetches the server JAR from cache, fetching it if the cache missed.
     *
     * @return the file, null if an error occurred
     */
    private fun fetchServerJar(): File? {
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
                it.copyTo(file.toPath())

                logger.info { "fetched ${workspace.version.id} vanilla JAR" }
                return file
            }

            logger.info { "failed to fetch ${workspace.version.id} vanilla JAR, received ${it.responseCode}" }
        }

        return null
    }

    companion object {
        /**
         * The file name of the cached server JAR.
         */
        const val SERVER = "server.jar"

        /**
         * The namespace of the class modifiers.
         */
        const val NS_MODIFIERS = "modifiers"

        /**
         * The namespace of the class generic signature.
         */
        const val NS_SIGNATURE = "signature"
    }
}
