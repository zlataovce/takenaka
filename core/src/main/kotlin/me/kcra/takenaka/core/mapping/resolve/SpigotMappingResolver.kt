package me.kcra.takenaka.core.mapping.resolve

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.*
import mu.KotlinLogging
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * A base resolver for Spigot mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
abstract class AbstractSpigotMappingResolver(
    val workspace: VersionedWorkspace,
    private val objectMapper: ObjectMapper,
    private val readLock: Lock = ReentrantLock()
) : MappingResolver {
    override val version: Version by workspace::version

    /**
     * The version manifest.
     */
    val manifest by lazy(::readManifest)

    /**
     * The version attributes.
     */
    val attributes by lazy(::readAttributes)

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

        // Spigot's stash doesn't seem to support sending Content-Length headers, so we'll need to re-fetch the mappings every time
        // that isn't very efficient, but it doesn't make a large difference in the time it takes (around +3 seconds)
        // perhaps we could provide a way to modify this behavior?

        val conn = URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/$mappingAttribute?at=${manifest.refs["BuildData"]}")
            .openConnection() as HttpURLConnection

        conn.requestMethod = "GET"
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    conn.inputStream.use {
                        Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    logger.info { "fetched ${version.id} Spigot mappings ($mappingAttribute)" }
                    return file.reader()
                }

                else -> logger.warn { "failed to fetch ${version.id} Spigot mappings ($mappingAttribute), received ${conn.responseCode}" }
            }
        } finally {
            conn.disconnect()
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
        return reader()?.buffered()?.use { it.readLine().reader() }
    }

    /**
     * Reads the manifest of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the manifest
     */
    private fun readManifest(): SpigotVersionManifest {
        // synchronize on the version instance, so both the class and member mapping resolvers
        // don't try to fetch the same files simultaneously
        readLock.withLock {
            val file = workspace[MANIFEST]

            if (MANIFEST in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionManifest>(file).apply {
                        logger.info { "read cached ${version.id} Spigot manifest" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${version.id} Spigot manifest, fetching it again" }
                }
            }

            val content = URL("https://hub.spigotmc.org/versions/${version.id}.json").readText()
            file.writeText(content)

            logger.info { "fetched ${version.id} Spigot manifest" }
            return objectMapper.readValue(content)
        }
    }

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): SpigotVersionAttributes {
        // synchronize on the version instance, so both the class and member mapping resolvers
        // don't try to fetch the same files simultaneously
        readLock.withLock {
            val file = workspace[INFO]

            if (INFO in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionAttributes>(file).apply {
                        logger.info { "read cached ${version.id} Spigot attributes" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${version.id} Spigot attributes, fetching them again" }
                }
            }

            val content = URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/info.json?at=${manifest.refs["BuildData"]}").readText()
            file.writeText(content)

            logger.info { "fetched ${version.id} Spigot attributes" }
            return objectMapper.readValue(content)
        }
    }

    companion object {
        /**
         * The file name of the cached version manifest.
         */
        const val MANIFEST = "manifest.json"

        /**
         * The file name of the cached version attributes.
         */
        const val INFO = "info.json"
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
    objectMapper: ObjectMapper,
    readLock: Lock
) : AbstractSpigotMappingResolver(workspace, objectMapper, readLock) {
    override val mappingAttribute: String
        get() = attributes.classMappings
    override val mappingAttributeName: String
        get() = "classMappings"
}

/**
 * A resolver for Spigot member mapping files.
 *
 * @property workspace the workspace
 * @author Matouš Kučera
 */
class SpigotMemberMappingResolver(
    workspace: VersionedWorkspace,
    objectMapper: ObjectMapper,
    readLock: Lock
) : AbstractSpigotMappingResolver(workspace, objectMapper, readLock) {
    override val mappingAttribute: String?
        get() = attributes.memberMappings
    override val mappingAttributeName: String
        get() = "memberMappings"
}
