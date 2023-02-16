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
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.generator.common.cli.CLI
import me.kcra.takenaka.generator.web.WebGenerator
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

        val generator = WebGenerator(
            output,
            versions,
            mappingWorkspace,
            { versionWorkspace, objectMapper ->
                listOf(
                    MojangServerMappingResolver(versionWorkspace, objectMapper),
                    IntermediaryMappingResolver(versionWorkspace),
                    SeargeMappingResolver(versionWorkspace),
                    SpigotClassMappingResolver(versionWorkspace, objectMapper),
                    SpigotMemberMappingResolver(versionWorkspace, objectMapper),
                    VanillaMappingContributor(versionWorkspace, objectMapper)
                )
            },
            coroutineDispatcher,
            transformers,
            listOf("mojang", "spigot", "searge", "intermediary"),
            mapOf(
                "mojang" to "rgb(77 124 15)",
                "spigot" to "rgb(202 138 4)",
                "searge" to "rgb(185 28 28)",
                "intermediary" to "rgb(3 105 161)",
                "source" to "rgb(88 28 135)"
            ),
            mapOf(
                "mojang" to "Mojang",
                "spigot" to "Spigot",
                "searge" to "Searge",
                "intermediary" to "Intermediary",
                "source" to "Obfuscated"
            )
        )

        generator.generate()
    }
}