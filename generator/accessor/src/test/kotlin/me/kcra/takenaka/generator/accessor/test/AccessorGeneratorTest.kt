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

package me.kcra.takenaka.generator.accessor.test

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.compositeWorkspace
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.resolve.impl.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.model.ConstructorAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor
import me.kcra.takenaka.generator.common.ResolvingMappingProvider
import me.kcra.takenaka.generator.common.buildMappingConfig
import kotlin.test.Test

class AccessorGeneratorTest {
    val workspace = compositeWorkspace {
        rootDirectory("accessor-test")
    }

    val cacheWorkspace = workspace.createCompositeWorkspace {
        name = "cache"
    }
    val mappingsCache = cacheWorkspace.createCompositeWorkspace {
        name = "mappings"
    }
    val sharedCache = cacheWorkspace.createWorkspace {
        name = "shared"
    }

    val resultWorkspace = workspace.createCompositeWorkspace {
        name = "result"
    }

    @Test
    fun `generate accessors`() {
        val objectMapper = objectMapper()
        val xmlMapper = XmlMapper()

        val yarnProvider = YarnMetadataProvider(sharedCache, xmlMapper)
        val mappingConfig = buildMappingConfig {
            version("1.19.4", "1.19.3")
            workspace(mappingsCache)

            // remove Searge's ID namespace, it's not necessary
            intercept { v ->
                NamespaceFilter(v, "searge_id")
            }
            // remove static initializers, not needed in the documentation
            intercept(::StaticInitializerFilter)
            // remove overrides of java/lang/Object, they are implicit
            intercept(::ObjectOverrideFilter)
            // remove obfuscated method parameter names, they are a filler from Searge
            intercept(::MethodArgSourceFilter)

            contributors { versionWorkspace ->
                val mojangProvider = MojangManifestAttributeProvider(versionWorkspace, objectMapper)
                val spigotProvider = SpigotManifestProvider(versionWorkspace, objectMapper)

                val prependedClasses = mutableListOf<String>()

                listOf(
                    VanillaMappingContributor(versionWorkspace, mojangProvider),
                    MojangServerMappingResolver(versionWorkspace, mojangProvider),
                    IntermediaryMappingResolver(versionWorkspace, sharedCache),
                    YarnMappingResolver(versionWorkspace, yarnProvider),
                    SeargeMappingResolver(versionWorkspace, sharedCache),
                    WrappingContributor(SpigotClassMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                        // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
                        // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
                        LegacySpigotMappingPrepender(it, prependedClasses = prependedClasses, prependEverything = versionWorkspace.version.id == "1.16.5")
                    },
                    WrappingContributor(SpigotMemberMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                        LegacySpigotMappingPrepender(it, prependedClasses = prependedClasses)
                    }
                )
            }
        }

        val mappingProvider = ResolvingMappingProvider(mappingConfig, objectMapper, xmlMapper)
        val analyzer = MappingAnalyzerImpl(
            MappingAnalyzerImpl.AnalysisOptions(
                innerClassNameCompletionCandidates = setOf("spigot")
            )
        )

        val generator = AccessorGenerator(
            resultWorkspace,
            AccessorConfiguration(
                listOf(
                    ClassAccessor(
                        "net.minecraft.network.protocol.game.ClientboundAddEntityPacket",
                        listOf(
                            FieldAccessor("x"),
                            FieldAccessor("y"),
                            FieldAccessor("z")
                        ),
                        listOf(
                            ConstructorAccessor("(Lnet/minecraft/world/entity/Entity;)V")
                        ),
                        listOf(
                            MethodAccessor("getId", "()")
                        )
                    )
                ),
                "me.kcra.takenaka.accessors",
                namespaceFriendlinessIndex = listOf("mojang", "spigot", "yarn", "searge", "intermediary", "source"),
                accessedNamespaces = listOf("spigot", "source"),
                craftBukkitVersionReplaceCandidates = listOf("spigot")
            )
        )

        runBlocking {
            val mappings = mappingProvider.get(analyzer)
            analyzer.acceptResolutions()

            generator.generate(mappings)
        }
    }
}
