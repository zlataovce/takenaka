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

@file:OptIn(ExperimentalCoroutinesApi::class)

package me.kcra.takenaka.generator.web.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.LegacySpigotMappingPrepender
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.common.cli.CLI
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.transformers.Minifier
import me.kcra.takenaka.generator.web.transformers.Transformer
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A CLI implementation of [WebGenerator].
 *
 * @author Matouš Kučera
 */
class WebCLI : CLI {
    /**
     * Launches the generator.
     *
     * @param output the workspace where the generator can freely move around
     * @param versions the versions to be processed
     * @param mappingWorkspace the workspace where the generator can cache mappings
     */
    override fun generate(output: Workspace, versions: List<String>, mappingWorkspace: CompositeWorkspace) {
        val isProduction = System.getProperty("me.kcra.takenaka.generator.web.env", "development").lowercase() == "production"
        logger.info { "Building in ${if (isProduction) "production" else "development"} mode" }

        val transformers = mutableListOf<Transformer>()
        if (isProduction) {
            transformers += Minifier()
        }

        val concurrencyLimit = System.getProperty("me.kcra.takenaka.generator.web.concurrencyLimit", "-1").toInt()
        val coroutineDispatcher = if (concurrencyLimit != -1) Dispatchers.IO.limitedParallelism(concurrencyLimit) else Dispatchers.IO

        val indexers = mutableListOf<ClassSearchIndex>()
        val indexerMapper = objectMapper()

        val configuredIndexers = System.getProperty("me.kcra.takenaka.generator.web.index.foreign", "")
        if (configuredIndexers.isNotBlank()) {
            indexers += configuredIndexers.split(',')
                .map { indexerDef ->
                    val options = indexerDef.split('+', limit = 2)

                    if (options.size == 2) {
                        classSearchIndexOf(options[0], options[1])
                    } else {
                        indexerMapper.modularClassSearchIndexOf(options[0])
                    }
                }
        }

        if (System.getProperty("me.kcra.takenaka.generator.web.index.jdk", "true").toBoolean()) {
            indexers += indexerMapper.modularClassSearchIndexOf(JDK_17_BASE_URL)
        }

        val skipSynthetics = System.getProperty("me.kcra.takenaka.generator.web.skipSynthetics", "true").toBoolean()

        val generator = WebGenerator(
            output,
            versions,
            mappingWorkspace,
            { versionWorkspace, objectMapper ->
                listOf(
                    MojangServerMappingResolver(versionWorkspace, objectMapper),
                    IntermediaryMappingResolver(versionWorkspace),
                    SeargeMappingResolver(versionWorkspace),
                    WrappingContributor(SpigotClassMappingResolver(versionWorkspace, objectMapper)) {
                        // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
                        // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
                        LegacySpigotMappingPrepender(it, prependAll = versionWorkspace.version.id == "1.16.5")
                    },
                    SpigotMemberMappingResolver(versionWorkspace, objectMapper),
                    VanillaMappingContributor(versionWorkspace, objectMapper)
                )
            },
            coroutineDispatcher,
            transformers,
            listOf("mojang", "spigot", "searge", "intermediary"),
            mapOf(
                "mojang" to "#4D7C0F",
                "spigot" to "#CA8A04",
                "searge" to "#B91C1C",
                "intermediary" to "#0369A1",
                "source" to "#581C87"
            ),
            mapOf(
                "mojang" to "Mojang",
                "spigot" to "Spigot",
                "searge" to "Searge",
                "intermediary" to "Intermediary",
                "source" to "Obfuscated"
            ),
            compositeClassSearchIndexOf(*indexers.toTypedArray()),
            skipSynthetics
        )

        generator.generate()
    }
}
