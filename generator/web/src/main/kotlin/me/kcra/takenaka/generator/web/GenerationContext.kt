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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.mapping.dstNamespaceIds
import me.kcra.takenaka.core.util.hexValue
import me.kcra.takenaka.core.util.threadLocalMessageDigest
import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * A thread-local MD5 digest.
 */
val md5Digest by threadLocalMessageDigest("MD5")

/**
 * A generation context.
 *
 * @author Matouš Kučera
 */
class GenerationContext(coroutineScope: CoroutineScope, val generator: WebGenerator, val styleConsumer: StyleConsumer) : CoroutineScope by coroutineScope {
    val index: ClassSearchIndex by generator::index

    /**
     * Gets a "friendly" destination name of an element.
     *
     * @param elem the element
     * @return the name
     */
    fun getFriendlyDstName(elem: MappingTreeView.ElementMappingView): String {
        generator.namespaceFriendlinessIndex.forEach { ns ->
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
    fun getNamespaceFriendlyName(ns: String): String? = generator.namespaces[ns]?.friendlyName

    /**
     * Gets a CSS color of the supplied namespace.
     *
     * @param ns the namespace
     * @return the color
     */
    fun getNamespaceBadgeColor(ns: String): String = generator.namespaces[ns]?.color ?: "#94a3b8"

    /**
     * Gets a CSS color of the namespace with the supplied friendly name.
     *
     * @param ns the namespace friendly name
     * @return the color
     */
    fun getFriendlyNamespaceBadgeColor(ns: String): String = generator.namespacesByFriendlyNames[ns]?.color ?: "#94a3b8"

    /**
     * Computes a hash of all destination mappings of this element.
     *
     * The resulting hash is stable, meaning the order of namespaces won't affect it.
     */
    val MappingTreeView.ElementMappingView.hash: String
        get() = md5Digest
            .apply {
                update(
                    tree.dstNamespaceIds
                        .mapNotNull { getDstName(it) }
                        .sorted()
                        .joinToString(",")
                        .encodeToByteArray()
                )
            }
            .hexValue
}

/**
 * Opens a generation context.
 *
 * @param styleConsumer the style provider that will be used in the context
 * @param block the context user
 */
inline fun <R> WebGenerator.generationContext(noinline styleConsumer: StyleConsumer, crossinline block: suspend GenerationContext.() -> R): R =
    runBlocking(coroutineDispatcher) { block(GenerationContext(this, this@generationContext, styleConsumer)) }
