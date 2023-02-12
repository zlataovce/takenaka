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

package me.kcra.takenaka.generator.web

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.html.dom.write
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ContributorProvider
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.util.MappingTreeRemapper
import me.kcra.takenaka.generator.common.AbstractGenerator
import me.kcra.takenaka.generator.web.pages.classPage
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree
import org.w3c.dom.Document
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * A generator that generates documentation in an HTML format.
 *
 * @param workspace the workspace in which this generator can move around
 * @param mappingWorkspace the workspace in which the mappings are stored
 * @param versions the Minecraft versions that this generator will process
 * @param namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @param namespaceBadgeColors a map of namespaces and their colors, defaults to #94a3b8 for all of them
 * @param namespaceFriendlyNames a map of namespaces and their names that will be shown in the documentation, unspecified namespaces will not be shown
 * @author Matouš Kučera
 */
class WebGenerator(
    workspace: Workspace,
    versions: List<String>,
    mappingWorkspace: CompositeWorkspace,
    contributorProvider: ContributorProvider,
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val namespaceBadgeColors: Map<String, String> = emptyMap(),
    val namespaceFriendlyNames: Map<String, String> = emptyMap()
) : AbstractGenerator(workspace, versions, mappingWorkspace, contributorProvider) {
    /**
     * Launches the generator.
     */
    override fun generate() {
        val composite = workspace.asComposite()

        runBlocking {
            mappings.forEach { (version, tree) ->
                val versionWorkspace = composite.versioned(version)
                val friendlyNameRemapper = MappingTreeRemapper(tree, ::getFriendlyDstName)

                launch {
                    tree.classes.forEach { klass ->
                        // skip mappings without modifiers, those weren't in the server JAR
                        if (klass.getName(VanillaMappingContributor.NS_MODIFIERS) == null) {
                            logger.warn { "Skipping generation for class ${getFriendlyDstName(klass)}, missing modifiers" }
                        } else {
                            classPage(versionWorkspace, friendlyNameRemapper, klass).serialize(versionWorkspace, "${getFriendlyDstName(klass)}.html")
                        }
                    }
                }
            }
        }

        workspace["assets"].mkdirs()

        fun copyAsset(name: String) {
            Files.copy(
                javaClass.getResourceAsStream("/assets/$name") ?: error("Could not copy over /assets/$name from resources"),
                workspace["assets/$name"].toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        copyAsset("main.css")
    }

    /**
     * Serializes a [Document] to a file in the specified workspace.
     *
     * @param workspace the workspace
     * @param path the path, relative in the workspace
     */
    fun Document.serialize(workspace: Workspace, path: String) {
        val file = workspace[path]
        file.parentFile.mkdirs()
        file.writer().use { it.write(this, prettyPrint = false) }
    }

    /**
     * Gets a "friendly" destination name of an element.
     *
     * @param elem the element
     * @return the name
     */
    fun getFriendlyDstName(elem: MappingTree.ElementMapping): String {
        namespaceFriendlinessIndex.forEach { ns ->
            elem.getName(ns)?.let { return it }
        }
        return (0 until elem.tree.maxNamespaceId).firstNotNullOfOrNull(elem::getDstName) ?: elem.srcName
    }
}
