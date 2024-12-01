/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.core.mapping.resolve.impl.craftBukkitNmsVersion
import me.kcra.takenaka.generator.common.provider.AncestryProvider
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
class GenerationContext(
    val generator: WebGenerator,
    val ancestryProvider: AncestryProvider,
    val styleProvider: StyleProvider?
) {
    /**
     * A [Set] variant of the [generator]'s [WebConfiguration.craftBukkitVersionReplaceCandidates].
     */
    internal val versionReplaceCandidates = generator.config.craftBukkitVersionReplaceCandidates.toSet()

    /**
     * Serializes a [Document] to a file in the specified workspace.
     *
     * @param workspace the workspace
     * @param path the path, relative in the workspace
     */
    suspend fun Document.serialize(workspace: Workspace, path: String) {
        withContext(Dispatchers.IO + CoroutineName("io-coro")) {
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
        fun getName(elem: MappingTreeView.ElementMappingView, ns: String): String? {
            val name = elem.getName(ns) ?: return null
            if (elem is MappingTreeView.ClassMappingView && ns in versionReplaceCandidates) {
                return name.replaceCraftBukkitNMSVersion(elem.tree.craftBukkitNmsVersion)
            }

            return name
        }

        generator.config.namespaceFriendlinessIndex.forEach { ns -> getName(elem, ns)?.let { return it } }

        // we didn't find a preferable name, grab anything
        return elem.tree.dstNamespaces.firstNotNullOfOrNull { ns -> getName(elem, ns) } ?: elem.srcName
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

    /**
     * Gets a relative path to the website root from a package name (page).
     *
     * @param packageName the package name
     * @return the relative path, with a trailing slash
     */
    fun getPackageRelativeRoot(packageName: String): String {
        return "../${getPackageRelativeVersionRoot(packageName)}"
    }

    /**
     * Gets a relative path to the version root from a package name (page).
     *
     * @param packageName the package name
     * @return the relative path, with a trailing slash
     */
    fun getPackageRelativeVersionRoot(packageName: String): String {
        if (packageName.isEmpty()) {
            return "" // there's no package
        }

        return "../".repeat(packageName.count { it == '.' || it == '/' } + 1)
    }

    /**
     * Gets a relative path to the website root from a class name (page).
     *
     * @param className the class name
     * @return the relative path, with a trailing slash
     */
    fun getClassRelativeRoot(className: String): String {
        return "../${getClassRelativeVersionRoot(className)}"
    }

    /**
     * Gets a relative path to the version root from a class name (page).
     *
     * @param className the class name
     * @return the relative path, with a trailing slash
     */
    fun getClassRelativeVersionRoot(className: String): String {
        return "../".repeat(className.count { it == '.' || it == '/' })
    }

    /**
     * Gets a relative path from a class name (page) to another name (page).
     *
     * **The classes must be from the same version and both names must be of internal format ([me.kcra.takenaka.core.mapping.toInternalName]).**
     *
     * @param origin the originating class name, the current location
     * @param destination the destination class name, the location where we want to be
     * @return the relative path, with a trailing slash
     */
    fun getClassRelativePath(origin: String, destination: String): String {
        val commonPackage = destination.commonPrefixWith(origin).substringBeforeLast('/') // strip last package that wasn't matched completely
        if (commonPackage.isEmpty()) {
            return "../".repeat(origin.count { it == '/' }) + "$destination.html"
        }

        val originRelativeName = origin.removePrefix("$commonPackage/")
        val destRelativeName = destination.removePrefix("$commonPackage/")
        return "../".repeat(originRelativeName.count { it == '/' }) + "$destRelativeName.html"
    }
}

/**
 * Opens a generation context.
 *
 * @param ancestryProvider the ancestry provider that will be used in the context
 * @param styleProvider the style provider that will be used in the context
 * @param block the context user
 */
suspend inline fun <R> WebGenerator.generationContext(
    ancestryProvider: AncestryProvider,
    styleProvider: StyleProvider? = null,
    crossinline block: suspend GenerationContext.() -> R
): R = coroutineScope {
    block(GenerationContext(this@generationContext, ancestryProvider, styleProvider))
}
