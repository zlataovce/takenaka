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

package me.kcra.takenaka.core.test.mapping.resolve

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.*
import me.kcra.takenaka.core.*
import me.kcra.takenaka.core.mapping.VersionedMappingMap
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.buildMappingTree
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.util.objectMapper
import net.fabricmc.mappingio.format.Tiny2Reader
import net.fabricmc.mappingio.format.Tiny2Writer
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writer
import kotlin.system.measureTimeMillis
import kotlin.test.Test

val VERSIONS = listOf(
    "1.19.4",
    // "1.19.3",
    // "1.19.2",
    // "1.19.1",
    // "1.19",
    "1.18.2",
    // "1.18.1",
    // "1.18",
    "1.17.1",
    // "1.17",
    "1.16.5",
    // "1.16.4",
    // "1.16.3",
    // "1.16.2",
    // "1.16.1",
    "1.15.2",
    // "1.15.1",
    // "1.15",
    "1.14.4",
    // "1.14.3",
    // "1.14.2",
    // "1.14.1",
    // "1.14",
    "1.13.2",
    // "1.13.1",
    // "1.13",
    "1.12.2",
    // "1.12.1",
    // "1.12",
    "1.11.2",
    // "1.11.1",
    // "1.11",
    "1.10.2",
    // "1.10",
    "1.9.4",
    // "1.9.2",
    // "1.9",
    "1.8.8"
)

class MappingResolverTest {
    private val objectMapper = objectMapper()
    private val xmlMapper = XmlMapper()
    private val workspaceDir = "test-workspace"

    @Test
    fun `resolve mappings for supported versions`() {
        val workspace = compositeWorkspace {
            rootDirectory(workspaceDir)

            resolverOptions {
                relaxedCache()
            }
        }

        val time = measureTimeMillis {
            workspace.resolveMappings(objectMapper, xmlMapper)
        }
        val cachedTime = measureTimeMillis {
            workspace.resolveMappings(objectMapper, xmlMapper)
        }

        println("Elapsed ${time / 1000}s, cached ${cachedTime / 1000}s")
    }
}

suspend fun VersionedWorkspace.resolveVersionMappings(objectMapper: ObjectMapper, xmlMapper: ObjectMapper): MappingTree = coroutineScope {
    return@coroutineScope buildMappingTree {
        val _prependedClasses = mutableListOf<String>()

        contributor(listOf(
            MojangServerMappingResolver(this@resolveVersionMappings, objectMapper),
            IntermediaryMappingResolver(this@resolveVersionMappings),
            YarnMappingResolver(this@resolveVersionMappings, xmlMapper),
            SeargeMappingResolver(this@resolveVersionMappings),
            // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
            // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
            WrappingContributor(SpigotClassMappingResolver(this@resolveVersionMappings, objectMapper, xmlMapper)) {
                LegacySpigotMappingPrepender(it, prependedClasses = _prependedClasses)
            },
            WrappingContributor(SpigotMemberMappingResolver(this@resolveVersionMappings, objectMapper, xmlMapper)) {
                LegacySpigotMappingPrepender(it, prependedClasses = _prependedClasses)
            },
            VanillaMappingContributor(this@resolveVersionMappings, objectMapper)
        ))

        interceptBefore { tree ->
            NamespaceFilter(MissingDescriptorFilter(MethodArgSourceFilter(tree)), "searge_id")
        }

        interceptAfter { tree ->
            tree.removeElementsWithoutModifiers()
            tree.removeSyntheticElements()
            tree.removeStaticInitializers()
            tree.completeInnerClassNames("spigot")

            tree.dstNamespaces.forEach { ns ->
                if (ns in VanillaMappingContributor.NAMESPACES) return@forEach

                tree.completeMethodOverrides(ns)
            }
        }
    }
}

fun CompositeWorkspace.resolveMappings(objectMapper: ObjectMapper, xmlMapper: ObjectMapper, save: Boolean = false): VersionedMappingMap = runBlocking {
    val manifest = objectMapper.versionManifest()
    val jobs = mutableListOf<Deferred<Pair<Version, MappingTree>>>()

    VERSIONS.forEach {
        val version = manifest[it] ?: error("did not find $it in manifest")

        jobs += async(Dispatchers.IO) {
            val workspace by createVersioned {
                this.version = version
            }
            val savedFile = workspace["joined.tiny"]
            if (save && savedFile.isRegularFile()) {
                return@async version to MemoryMappingTree().apply { Tiny2Reader.read(savedFile.reader(), this) }
            }

            val tree = workspace.resolveVersionMappings(objectMapper, xmlMapper)

            if (save) {
                Tiny2Writer(savedFile.writer(), false)
                    .use { writer -> tree.accept(writer) }
            }

            return@async version to tree
        }
    }

    return@runBlocking jobs.awaitAll().toMap()
}
