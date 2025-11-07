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
import me.kcra.takenaka.core.mapping.resolve.AbstractOutputContainer
import me.kcra.takenaka.core.mapping.resolve.Output
import me.kcra.takenaka.core.mapping.resolve.lazyOutput
import me.kcra.takenaka.core.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

private val logger = KotlinLogging.logger {}

/**
 * A base mapping contributor that completes any missing names and descriptors,
 * and visits modifiers and the generic signature as mappings.
 *
 * @property workspace the workspace
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
abstract class AbstractVanillaMappingContributor(
    val workspace: VersionedWorkspace,
    val relaxedCache: Boolean = true,
    val prohibitUsageOfMojang: Boolean = false,
) : AbstractOutputContainer<Path?>(), MappingContributor {
    override val targetNamespace: String = MappingUtil.NS_SOURCE_FALLBACK
    override val outputs: List<Output<out Path?>>
        get() = listOf(jarOutput)

    /**
     * The resolved JAR attribute.
     */
    val jarAttribute by lazy(::resolveJarAttribute)

    /**
     * The vanilla JAR output.
     */
    val jarOutput = lazyOutput<Path?> {
        resolver {
            if (!jarAttribute.exists) {
                logger.info { "did not find vanilla JAR for ${workspace.version.id} (${jarAttribute.name})" }
                return@resolver null
            }

            val fileName = "${jarAttribute.name}.jar"
            val file = workspace[fileName]

            withContext(Dispatchers.IO + CoroutineName("resolve-coro")) {
                if (fileName in workspace) {
                    val checksum = file.getChecksum(sha1Digest)

                    if (jarAttribute.checksum == checksum) {
                        logger.info { "matched checksum for cached ${workspace.version.id} vanilla JAR (name: ${jarAttribute.name}, value: ${jarAttribute.value})" }
                        return@withContext file
                    }

                    logger.warn { "checksum mismatch for ${workspace.version.id} vanilla JAR cache, fetching it again (name: ${jarAttribute.name}, value: ${jarAttribute.value})" }
                }

                URL(jarAttribute.value!!).httpRequest {
                    if (it.ok) {
                        it.copyTo(file)

                        logger.info { "fetched ${workspace.version.id} vanilla JAR (name: ${jarAttribute.name}, value: ${jarAttribute.value})" }
                        return@httpRequest file
                    }

                    logger.info { "failed to fetch ${workspace.version.id} vanilla JAR (name: ${jarAttribute.name}, value: ${jarAttribute.value}), received ${it.responseCode}" }
                    return@httpRequest null
                }
            }
        }

        upToDateWhen { it == null || it.isRegularFile() }
    }

    /**
     * Resolves a Mojang manifest JAR attribute.
     *
     * @return the JAR attribute
     */
    protected abstract fun resolveJarAttribute(): ManifestAttribute

    /**
     * Visits the original mappings to the supplied visitor.
     *
     * [NS_SUPER] is null if the superclass is `java/lang/Object`.
     * [NS_INTERFACES] is null if there are no superinterfaces.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        val jarPath by jarOutput

        fun read(zf: ZipFile) {
            val useMojangNamespace = jarAttribute.isUnobfuscated && !prohibitUsageOfMojang

            val classVisitor = MappingClassVisitor(
                visitor,
                if (useMojangNamespace) "mojang" else targetNamespace,
                !useMojangNamespace
            )

            zf.entries()
                .asSequence()
                .filter { !it.isDirectory && it.name.matches(CLASS_PATTERN) }
                .forEach { entry ->
                    zf.getInputStream(entry).use { inputStream ->
                        // ignore any method content and debugging data
                        ClassReader(inputStream).accept(classVisitor, ClassReader.SKIP_CODE or (if (useMojangNamespace) 0 else ClassReader.SKIP_DEBUG))
                    }
                }
        }

        jarPath?.let { file ->
            ZipFile(file.toFile()).use { zf ->
                if (zf.getEntry("net/minecraft/bundler/Main.class") != null) {
                    val bundledFile = file.resolveSibling(file.nameWithoutExtension + "-bundled.jar")

                    if (!relaxedCache || !bundledFile.isRegularFile()) {
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
        // include only com.mojang.[math|blaze3d|realmsclient].* (DFU, Brigadier and others are open source), net.minecraft.* and classes without a package (obfuscated)
        private val CLASS_PATTERN = "com/mojang/(?:math|blaze3d|realmsclient)/.*\\.class|net/minecraft/.*\\.class|[^/]+\\.class".toRegex()

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
    class MappingClassVisitor(val visitor: MappingVisitor, sourceNs: String, val skipParams: Boolean) : ClassVisitor(Opcodes.ASM9) {
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

                if (!skipParams) {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        var index = 0

                        override fun visitParameter(name: String?, access2: Int) {
                            if (name != null && name.isNotBlank()) {
                                visitor.visitMethodArg(
                                    index,
                                    if ((access and Opcodes.ACC_STATIC) != 0) index else (index + 1),
                                    name
                                )
                            }
                            index++
                        }
                    }
                }
            }

            return null
        }
    }
}

/**
 * Gets the modifiers from the [AbstractVanillaMappingContributor.NS_MODIFIERS] namespace.
 */
inline val MappingTreeView.ElementMappingView.modifiers: Int
    get() = getName(AbstractVanillaMappingContributor.NS_MODIFIERS)?.toIntOrNull() ?: 0

/**
 * Gets the generic signature from the [AbstractVanillaMappingContributor.NS_SIGNATURE] namespace.
 */
inline val MappingTreeView.ElementMappingView.signature: String?
    get() = getName(AbstractVanillaMappingContributor.NS_SIGNATURE)

/**
 * Gets the superclass from the [AbstractVanillaMappingContributor.NS_SUPER] namespace.
 */
inline val MappingTreeView.ClassMappingView.superClass: String
    get() = getName(AbstractVanillaMappingContributor.NS_SUPER) ?: "java/lang/Object"

/**
 * Gets interfaces from the [AbstractVanillaMappingContributor.NS_INTERFACES] namespace.
 */
inline val MappingTreeView.ClassMappingView.interfaces: List<String>
    get() = getName(AbstractVanillaMappingContributor.NS_INTERFACES).parseInterfaces()

/**
 * Parses interfaces per the [AbstractVanillaMappingContributor.NS_INTERFACES] format.
 *
 * @return the interfaces, empty if there were none
 */
fun String?.parseInterfaces(): List<String> = this?.split(',') ?: emptyList()

/**
 * A mapping contributor for vanilla client JARs.
 *
 * @param workspace the workspace
 * @property mojangProvider the Mojang manifest provider
 * @param relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
class VanillaClientMappingContributor(
    workspace: VersionedWorkspace,
    val mojangProvider: MojangManifestAttributeProvider,
    relaxedCache: Boolean = true,
    prohibitUsageOfMojang: Boolean = false
) : AbstractVanillaMappingContributor(workspace, relaxedCache, prohibitUsageOfMojang) {
    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Deprecated(
        "Jackson will be an implementation detail in the future.",
        ReplaceWith("VanillaClientMappingContributor(workspace, relaxedCache)")
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
     * Resolves a Mojang manifest JAR attribute.
     *
     * @return the JAR attribute
     */
    override fun resolveJarAttribute(): ManifestAttribute {
        return ManifestAttribute(
            "client",
            mojangProvider.attributes.downloads.client.url,
            mojangProvider.attributes.downloads.client.sha1,
            mojangProvider.attributes.isUnobfuscated
        )
    }
}

/**
 * A mapping contributor for vanilla server JARs.
 *
 * @param workspace the workspace
 * @property mojangProvider the Mojang manifest provider
 * @param relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
class VanillaServerMappingContributor(
    workspace: VersionedWorkspace,
    val mojangProvider: MojangManifestAttributeProvider,
    relaxedCache: Boolean = true,
    prohibitUsageOfMojang: Boolean = false
) : AbstractVanillaMappingContributor(workspace, relaxedCache, prohibitUsageOfMojang) {
    /**
     * Creates a new resolver with a default metadata provider.
     *
     * @param workspace the workspace
     * @param objectMapper an [ObjectMapper] that can deserialize JSON data
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Deprecated(
        "Jackson will be an implementation detail in the future.",
        ReplaceWith("VanillaServerMappingContributor(workspace, relaxedCache)")
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
     * Resolves a Mojang manifest JAR attribute.
     *
     * @return the JAR attribute
     */
    override fun resolveJarAttribute(): ManifestAttribute {
        return ManifestAttribute(
            "server",
            mojangProvider.attributes.downloads.server.url,
            mojangProvider.attributes.downloads.server.sha1,
            mojangProvider.attributes.isUnobfuscated
        )
    }
}
