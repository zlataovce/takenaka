package me.kcra.takenaka.core.mapping.resolve

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.*
import mu.KotlinLogging
import java.net.URL
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * A consumer of Spigot's BuildData manifest.
 *
 * @author Matouš Kučera
 */
open class SpigotManifestConsumer(
    val workspace: VersionedWorkspace,
    private val objectMapper: ObjectMapper
) {
    /**
     * The version manifest.
     */
    val manifest by lazy(::readManifest)

    /**
     * The version attributes.
     */
    val attributes by lazy(::readAttributes)

    /**
     * Reads the manifest of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the manifest
     */
    private fun readManifest(): SpigotVersionManifest {
        workspace.spigotManifestLock.withLock {
            val file = workspace[MANIFEST]

            if (RELAXED_CACHE in workspace.resolverOptions && MANIFEST in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionManifest>(file).apply {
                        logger.info { "read cached ${workspace.version.id} Spigot manifest" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} Spigot manifest, fetching it again" }
                }
            }

            val content = URL("https://hub.spigotmc.org/versions/${workspace.version.id}.json").readText()
            file.writeText(content)

            logger.info { "fetched ${workspace.version.id} Spigot manifest" }
            return objectMapper.readValue(content)
        }
    }

    /**
     * Reads the attributes of the targeted version from cache, fetching it if the cache missed.
     *
     * @return the attributes
     */
    private fun readAttributes(): SpigotVersionAttributes {
        workspace.spigotManifestLock.withLock {
            val file = workspace[BUILDDATA_INFO]

            if (RELAXED_CACHE in workspace.resolverOptions && BUILDDATA_INFO in workspace) {
                try {
                    return objectMapper.readValue<SpigotVersionAttributes>(file).apply {
                        logger.info { "read cached ${workspace.version.id} Spigot attributes" }
                    }
                } catch (e: JacksonException) {
                    logger.warn(e) { "failed to read cached ${workspace.version.id} Spigot attributes, fetching them again" }
                }
            }

            val content = URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/info.json?at=${manifest.refs["BuildData"]}").readText()
            file.writeText(content)

            logger.info { "fetched ${workspace.version.id} Spigot attributes" }
            return objectMapper.readValue(content)
        }
    }

    companion object {
        /**
         * The file name of the cached version manifest.
         */
        const val MANIFEST = "spigot_manifest.json"

        /**
         * The file name of the cached version attributes.
         */
        const val BUILDDATA_INFO = "spigot_builddata_info.json"
    }
}