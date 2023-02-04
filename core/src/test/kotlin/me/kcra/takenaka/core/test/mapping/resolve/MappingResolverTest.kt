package me.kcra.takenaka.core.test.mapping.resolve

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.manifestObjectMapper
import me.kcra.takenaka.core.mapping.resolve.IntermediaryMappingResolver
import me.kcra.takenaka.core.mapping.resolve.MojangServerMappingResolver
import me.kcra.takenaka.core.mapping.resolve.SeargeMappingResolver
import me.kcra.takenaka.core.versionManifest
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.BeforeTest

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
        "1.17.1",
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
        "1.8.8"
    )

    @BeforeTest
    fun `clean test residues`() {
        workspaceDir.deleteRecursively()
    }

    @Test
    fun `resolve mappings for supported versions`() {
        val workspace = CompositeWorkspace(workspaceDir)
        val manifest = objectMapper.versionManifest()

        suspend fun resolveVersionMappings(versionedWorkspace: VersionedWorkspace) = coroutineScope {
            val resolvers = listOf(
                MojangServerMappingResolver(versionedWorkspace, objectMapper),
                IntermediaryMappingResolver(versionedWorkspace),
                SeargeMappingResolver(versionedWorkspace)
            )

            // dry run the fetching without reading anything
            resolvers.forEach {
                launch(Dispatchers.IO) {
                    it.reader()?.close()
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
        val cachedTime = measureTimeMillis(::resolveMappings)

        println("Elapsed ${time / 1000}s, cached ${cachedTime / 1000}s")
    }
}