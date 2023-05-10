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

import kotlinx.coroutines.*
import kotlinx.html.dom.serialize
import kotlinx.html.dom.write
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.util.dstNamespaceIds
import net.fabricmc.mappingio.tree.MappingTreeView
import org.w3c.dom.Document
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.io.path.writer

/**
 * A generation context, not version-bound.
 *
 * @author Matouš Kučera
 */
class GenerationContext(val generator: WebGenerator, val styleConsumer: StyleConsumer, contextScope: CoroutineScope) : CoroutineScope by contextScope {
    /**
     * Serializes a [Document] to a file in the specified workspace.
     *
     * @param workspace the workspace
     * @param path the path, relative in the workspace
     */
    fun Document.serialize(workspace: Workspace, path: String) {
        launch(Dispatchers.IO + CoroutineName("save-coro")) {
            val file = workspace[path]
            file.parent.createDirectories()

            if (generator.config.transformers.isEmpty()) {
                file.writer().use { it.write(this@serialize) }
            } else {
                file.writeText(generator.transformHtml(serialize(prettyPrint = !generator.hasMinifier)))
            }
        }
    }

    /**
     * Gets a "friendly" destination name of an element.
     *
     * @param elem the element
     * @return the name
     */
    fun getFriendlyDstName(elem: MappingTreeView.ElementMappingView): String {
        generator.config.namespaceFriendlinessIndex.forEach { ns ->
            elem.getName(ns)?.let { return it }
        }
        return elem.tree.dstNamespaceIds.firstNotNullOfOrNull(elem::getDstName) ?: elem.srcName
    }

    /**
     * Gets a CSS color of the supplied namespace.
     *
     * @param ns the namespace
     * @return the color
     */
    fun getNamespaceFriendlyName(ns: String): String? = generator.config.namespaces[ns]?.friendlyName

    /**
     * Gets a CSS color of the supplied namespace.
     *
     * @param ns the namespace
     * @return the color
     */
    fun getNamespaceBadgeColor(ns: String): String = generator.config.namespaces[ns]?.color ?: "#94a3b8"

    /**
     * Gets a CSS color of the namespace with the supplied friendly name.
     *
     * @param ns the namespace friendly name
     * @return the color
     */
    fun getFriendlyNamespaceBadgeColor(ns: String): String = generator.namespacesByFriendlyNames[ns]?.color ?: "#94a3b8"
}

/**
 * Opens a generation context.
 *
 * @param styleConsumer the style provider that will be used in the context
 * @param block the context user
 */
suspend inline fun <R> WebGenerator.generationContext(noinline styleConsumer: StyleConsumer, crossinline block: suspend GenerationContext.() -> R): R =
    coroutineScope { block(GenerationContext(this@generationContext, styleConsumer, this)) }
