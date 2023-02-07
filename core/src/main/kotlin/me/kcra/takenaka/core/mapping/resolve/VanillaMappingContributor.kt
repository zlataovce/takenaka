package me.kcra.takenaka.core.mapping.resolve

import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.MappingContributor
import mu.KotlinLogging
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

private val logger = KotlinLogging.logger {}

class VanillaMappingContributor(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper
) : MojangManifestConsumer(workspace, objectMapper), MappingContributor {
    private val sha1Digest = MessageDigest.getInstance("SHA-1")
    override val targetNamespace: String = MappingUtil.NS_SOURCE_FALLBACK

    /**
     * Visits the original mappings to the supplied visitor.
     *
     * @param visitor the visitor
     */
    override fun accept(visitor: MappingVisitor) {
        TODO("Not yet implemented")
    }

    /**
     * Reads the server JAR (bundled JAR in case of a bundler) and visits it to the supplied visitor.
     *
     * @param visitor the class visitor
     */
    private fun readServerJar(visitor: ClassVisitor) {
        val file = fetchServerJar() ?: return

        fun readJar(i: InputStream) {

        }

        ZipFile(file).use { zf ->
            if (zf.getEntry("net/minecraft/bundler/Main.class") != null) {
                val bundledFile = file.resolveSibling(file.name + ".bundle")
                if (!bundledFile.isFile) {
                    zf.getInputStream(zf.getEntry("versions/${workspace.version.id}/server-${workspace.version.id}.jar")).use {
                        Files.copy(it, bundledFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                return
            }
        }

        readJar(file.inputStream())
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

        val conn = URL(attributes.downloads.server.url)
            .openConnection() as HttpURLConnection

        conn.requestMethod = "GET"
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    conn.inputStream.use {
                        Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    logger.info { "fetched ${workspace.version.id} vanilla JAR" }
                    return file
                }

                else -> logger.info { "failed to fetch ${workspace.version.id} vanilla JAR, received ${conn.responseCode}" }
            }
        } finally {
            conn.disconnect()
        }

        return null
    }

    companion object {
        /**
         * The file name of the cached server JAR.
         */
        const val SERVER = "server.jar"
    }
}
