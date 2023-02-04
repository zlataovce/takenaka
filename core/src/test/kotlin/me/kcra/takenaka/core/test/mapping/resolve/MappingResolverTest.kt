package me.kcra.takenaka.core.test.mapping.resolve

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.manifestObjectMapper
import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.VersionedMappingFile
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.versionManifest
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class MappingResolverTest {
    private val objectMapper = manifestObjectMapper()
    private val workspaceDir = File("test-workspace")
    private val versions = listOf(
        "1.19.3",
        "1.19.2",
        "1.19.1",
        "1.19",
        "1.18.2",
        "1.18.1",
        "1.18",
        /*"1.17.1",
        "1.17",
        "1.16.5",
        "1.16.4",
        "1.16.3",
        "1.16.2",
        "1.16.1",
        "1.15.2",
        "1.15.1",
        "1.15",
        "1.14.4",
        "1.14.3",
        "1.14.2",
        "1.14.1",
        "1.14",
        "1.13.2",
        "1.13.1",
        "1.13",
        "1.12.2",
        "1.12.1",
        "1.12",
        "1.11.2",
        "1.11.1",
        "1.11",
        "1.10.2",
        "1.10",
        "1.9.4",
        "1.9.2",
        "1.9",
        "1.8.8"*/
    )

    @Test
    fun `resolve mappings for supported versions`() {
        val workspace = CompositeWorkspace(workspaceDir)
        val manifest = objectMapper.versionManifest()

        val files = mutableListOf<VersionedMappingFile>()

        suspend fun resolveVersionMappings(versionedWorkspace: VersionedWorkspace) = coroutineScope {
            val spigotReadLock = ReentrantLock()
            val resolvers = listOf<MappingContributor>(
                MojangServerMappingResolver(versionedWorkspace, objectMapper),
                IntermediaryMappingResolver(versionedWorkspace),
                SeargeMappingResolver(versionedWorkspace),
                SpigotClassMappingResolver(versionedWorkspace, objectMapper, spigotReadLock),
                SpigotMemberMappingResolver(versionedWorkspace, objectMapper, spigotReadLock)
            )

            val file = VersionedMappingFile(versionedWorkspace.version)
            files += file

            val writeLock = ReentrantLock()

            resolvers.forEach {
                launch(Dispatchers.IO) {
                    writeLock.withLock {
                        it.accept(file)
                    }
                }
            }
        }

        fun resolveMappings() = runBlocking {
            versions.forEach {
                launch {
                    resolveVersionMappings(workspace.versioned(manifest[it] ?: error("did not find $it in manifest")))
                }
            }
        }

        val time = measureTimeMillis(::resolveMappings)
        files.clear()
        val cachedTime = measureTimeMillis(::resolveMappings)

        println("Elapsed ${time / 1000}s, cached ${cachedTime / 1000}s")

        assertEquals(versions.size, files.size, message = "wrong amount of mapping files was processed")
    }
}