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

@file:JvmName("Main")

package me.kcra.takenaka.generator.common.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import me.kcra.takenaka.core.*
import mu.KotlinLogging
import java.util.*
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * The application entrypoint.
 */
fun main(args: Array<String>) {
    val parser = ArgParser("takenaka")
    val output by parser.option(ArgType.String, shortName = "o", description = "Output directory").default("output")
    val versions by parser.option(ArgType.String, shortName = "v", description = "Target Minecraft versions, separated by commas").required()
    val mappingCache by parser.option(ArgType.String, shortName = "c", description = "Caching directory for mappings").default("mapping-cache")
    val strictCache by parser.option(ArgType.Boolean, description = "Restricts cache invalidation conditions").default(false)
    val clean by parser.option(ArgType.Boolean, description = "Removes previous build output before launching").default(false)

    parser.parse(args)

    val versionList = versions.split(',')
    val options = buildOptions {
        if (!strictCache) {
            relaxedCache()
        }
    }

    val workspace = workspace {
        rootDirectory(output)
        resolverOptions = options
    }
    val mappingWorkspace = compositeWorkspace {
        rootDirectory(mappingCache)
        resolverOptions = options
    }

    if (clean) {
        workspace.clean()
        mappingWorkspace.clean()
    }

    ServiceLoader.load(CLI::class.java).asSequence()
        .ifEmpty { error("Did not find any service implementations of me.kcra.takenaka.generator.common.cli.CLIFacade") }
        .forEach {
            logger.info { "Running generator ${it::class.qualifiedName}..." }
            val time = measureTimeMillis { it.generate(workspace, versionList, mappingWorkspace) }
            logger.info { "Generator ${it::class.qualifiedName} finished in ${time / 1000} seconds." }
        }
}
