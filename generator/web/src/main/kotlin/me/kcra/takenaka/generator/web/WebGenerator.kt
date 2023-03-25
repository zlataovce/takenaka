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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.html.dom.serialize
import kotlinx.html.dom.write
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.adapter.completeInnerClassNames
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.core.mapping.ancestry.classAncestryTreeOf
import me.kcra.takenaka.core.mapping.allNamespaceIds
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.mapping.resolve.modifiers
import me.kcra.takenaka.generator.common.AbstractGenerator
import me.kcra.takenaka.generator.common.ContributorProvider
import me.kcra.takenaka.generator.web.components.footerComponent
import me.kcra.takenaka.generator.web.components.navComponent
import me.kcra.takenaka.generator.web.pages.*
import me.kcra.takenaka.generator.web.transformers.Minifier
import me.kcra.takenaka.generator.web.transformers.Transformer
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView
import org.w3c.dom.Document
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.IdentityHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.io.path.writer

/**
 * A generator that generates documentation in an HTML format.
 *
 * An instance can be reused, but it is **not** thread-safe!
 *
 * @param workspace the workspace in which this generator can move around
 * @param versions the Minecraft versions that this generator will process
 * @param mappingWorkspace the workspace in which the mappings are stored
 * @param contributorProvider a function that provides mapping contributors to be processed
 * @param coroutineDispatcher the Kotlin Coroutines context
 * @param skipSynthetic whether synthetic classes and their members should be skipped
 * @param correctNamespaces namespaces excluded from any correction, these are artificial (non-mapping) namespaces defined in the core library by default
 * @param transformers a list of transformers that transform the output
 * @param namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @param namespaces a map of namespaces and their descriptions, unspecified namespaces will not be shown
 * @param index a resolver for foreign class references
 * @param spigotLikeNamespaces namespaces that should have [replaceCraftBukkitNMSVersion] and [completeInnerClassNames] applied (most likely Spigot mappings or a flavor of them)
 * @param historyAllowedNamespaces namespaces that should be used for computing history, empty if namespaces from [namespaceFriendlinessIndex] should be considered (excluding the obfuscated one)
 * @author Matouš Kučera
 */
class WebGenerator(
    workspace: Workspace,
    versions: List<String>,
    mappingWorkspace: CompositeWorkspace,
    contributorProvider: ContributorProvider,
    coroutineDispatcher: CoroutineContext = Dispatchers.IO,
    skipSynthetic: Boolean = false,
    correctNamespaces: List<String> = VanillaMappingContributor.NAMESPACES,
    val transformers: List<Transformer> = emptyList(),
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val namespaces: Map<String, NamespaceDescription> = emptyMap(),
    val index: ClassSearchIndex = emptyClassSearchIndex(),
    val spigotLikeNamespaces: List<String> = emptyList(),
    val historyAllowedNamespaces: List<String> = namespaceFriendlinessIndex - MappingUtil.NS_SOURCE_FALLBACK
) : AbstractGenerator(
    workspace,
    versions,
    mappingWorkspace,
    contributorProvider,
    coroutineDispatcher,
    skipSynthetic,
    correctNamespaces
) {
    private val namespaceFriendlyNames = namespaces.mapValues { it.value.friendlyName }
    private val hasMinifier = transformers.any { it is Minifier }

    /**
     * Launches the generator.
     */
    override fun generate() {
        val composite: CompositeWorkspace by workspace
        val styleSupplier = DefaultStyleProvider()

        val historyWorkspace by composite.createWorkspace {
            name = "history"
        }

        generationContext(styleProvider = styleSupplier::apply) {
            // first pass: complete inner class names for spigot-like namespaces
            // not done in AbstractGenerator due to the namespace requirement
            mappings.forEach { (_, tree) ->
                spigotLikeNamespaces.forEach { ns ->
                    if (tree.getNamespaceId(ns) != MappingTree.NULL_NAMESPACE_ID) {
                        tree.completeInnerClassNames(ns)
                    }
                }
            }

            val tree = classAncestryTreeOf(mappings, historyAllowedNamespaces)

            // second pass: replace the CraftBukkit NMS version for spigot-like namespaces
            // must be after ancestry tree computation, because replacing the VVV package breaks (the remaining) uniformity of the mappings
            mappings.forEach { (_, tree) ->
                spigotLikeNamespaces.forEach { ns ->
                    if (tree.getNamespaceId(ns) != MappingTree.NULL_NAMESPACE_ID) {
                        tree.replaceCraftBukkitNMSVersion(ns)
                    }
                }
            }

            // used for looking up history hashes - for linking
            val hashMap = IdentityHashMap<MappingTreeView.ClassMappingView, String>()

            tree.forEach { node ->
                val (_, firstKlass) = node.first
                val fileHash = firstKlass.hash.take(10)

                node.forEach { (_, klass) ->
                    hashMap[klass] = fileHash
                }

                launch(coroutineDispatcher) {
                    historyPage(node)
                        .serialize(historyWorkspace, "$fileHash.html")
                }
            }

            // third pass: generate the documentation
            mappings.forEach { (version, tree) ->
                launch(coroutineDispatcher) {
                    val versionWorkspace by composite.createVersioned {
                        this.version = version
                    }

                    val friendlyNameRemapper = ElementRemapper(tree, ::getFriendlyDstName)
                    val classMap = mutableMapOf<String, MutableMap<String, ClassType>>()

                    // class index format, similar to a CSV:
                    // first line is a "header", this is a tab-delimited string with friendly namespace names + its badge colors, which are delimited by a colon ("namespace:#color")
                    // following lines are tab-separated strings with the mappings, each substring belongs to its column (header.split("\t").get(substringIndex) -> "namespace:#color")
                    // the column order is based on namespaceFriendlinessIndex, so the first non-null column is the friendly name
                    // "net/minecraft" and "com/mojang" are replaced with "%nm" and "%cm" to reduce duplication

                    // example:
                    // Mojang:#4D7C0F   Intermediary:#0369A1    Obfuscated:#581C87
                    // %cm/math/Matrix3f %nm/class_4581    a
                    // %cm/math/Matrix4f %nm/class_1159    b
                    // ... (repeats like this for all classes)
                    val classIndex = buildString {
                        val namespaces = tree.allNamespaceIds
                            .mapNotNull { nsId ->
                                val nsName = tree.getNamespaceName(nsId)
                                if (nsName in namespaceFriendlyNames) nsName to nsId else null
                            }
                            .sortedBy { namespaceFriendlinessIndex.indexOf(it.first) }
                            .toMap()

                        appendLine(namespaces.keys.joinToString("\t") { "${namespaceFriendlyNames[it]}:${getNamespaceBadgeColor(it)}" })

                        tree.classes.forEach { klass ->
                            val friendlyName = getFriendlyDstName(klass)

                            launch(coroutineDispatcher) {
                                classPage(klass, versionWorkspace, friendlyNameRemapper)
                                    .serialize(versionWorkspace, "$friendlyName.html")
                            }

                            classMap.getOrPut(friendlyName.substringBeforeLast('/')) { mutableMapOf() } +=
                                friendlyName.substringAfterLast('/') to classTypeOf(klass.modifiers)

                            appendLine(
                                namespaces.values.joinToString("\t") { ns ->
                                    klass.getName(ns)
                                        ?.replace("net/minecraft", "%nm")
                                        ?.replace("com/mojang", "%cm")
                                        ?: ""
                                }
                            )
                        }
                    }

                    classMap.forEach { (packageName, classes) ->
                        packagePage(versionWorkspace, packageName, classes)
                            .serialize(versionWorkspace, "$packageName/index.html")
                    }

                    val licenses = namespaces
                        .mapNotNull { (ns, nsDesc) ->
                            if (nsDesc.license == null) return@mapNotNull null

                            val content = nsDesc.license.content.let(tree::getMetadata) ?: return@mapNotNull null
                            val source = nsDesc.license.source.let(tree::getMetadata) ?: return@mapNotNull null

                            return@mapNotNull ns to licenseOf(content, source)
                        }
                        .toMap()

                    licensePage(versionWorkspace, licenses)
                        .serialize(versionWorkspace, "licenses.html")

                    overviewPage(versionWorkspace, classMap.keys)
                        .serialize(versionWorkspace, "index.html")

                    versionWorkspace["class-index.js"].writeText("updateClassIndex(`${classIndex.trim()}`);") // do not minify this file
                }
            }

            versionsPage(mappings.mapValues { it.value.dstNamespaces })
                .serialize(workspace, "index.html")
        }

        workspace["assets"].createDirectories()

        fun copyAsset(name: String) {
            val inputStream = javaClass.getResourceAsStream("/assets/$name")
                ?: error("Could not copy over /assets/$name from resources")
            val destination = workspace["assets/$name"]

            if (transformers.isNotEmpty()) {
                var content = inputStream.bufferedReader().use(BufferedReader::readText)

                transformers.forEach { transformer ->
                    content = when (name.substringAfterLast('.')) {
                        "css" -> transformer.transformCss(content)
                        "js" -> transformer.transformJs(content)
                        "html" -> transformer.transformHtml(content)
                        else -> content
                    }
                }

                destination.writeText(content)
            } else {
                inputStream.use { Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING) }
            }
        }

        copyAsset("main.js")

        val componentFileContent = generateComponentFile(
            component {
                tag = "nav"
                content {
                    navComponent()
                }
                callback = """
                    const overviewLink = document.getElementById("overview-link");
                    const licensesLink = document.getElementById("licenses-link");
                    const searchInput = document.getElementById("search-input");
                    
                    if (baseUrl) {
                        overviewLink.href = baseUrl + "/index.html";
                        licensesLink.href = baseUrl + "/licenses.html";
                        searchInput.addEventListener("input", (evt) => search(evt.target.value));
                    } else {
                        overviewLink.remove();
                        licensesLink.remove();
                        searchInput.remove();
                        e.dataset.collapsed = "yes";
                    }
                """.trimIndent()
            },
            component {
                tag = "footer"
                content {
                    footerComponent()
                }
            }
        )
        workspace["assets/components.js"].writeText(componentFileContent.transformJs())
        workspace["assets/generated.css"].writeText(styleSupplier.generateStyleSheet().transformCss())

        copyAsset("main.css") // main.css should be copied last to minify correctly
    }

    /**
     * Serializes a [Document] to a file in the specified workspace.
     *
     * @param workspace the workspace
     * @param path the path, relative in the workspace
     */
    fun Document.serialize(workspace: Workspace, path: String) {
        val file = workspace[path]
        file.parent.createDirectories()

        if (transformers.isEmpty()) {
            file.writer().use { it.write(this) }
        } else {
            file.writeText(serialize(prettyPrint = !hasMinifier).transformHtml())
        }
    }

    /**
     * Generates a components.js file.
     *
     * @param components a list of tag-document-callback
     * @return the content of the component file
     */
    fun generateComponentFile(vararg components: ComponentDefinition): String = buildString {
        fun Document.serializeAsComponent(): String {
            var content = serialize(prettyPrint = false)
            transformers.forEach { transformer ->
                content = transformer.transformHtml(content)
            }

            return content.substringAfter("\n")
        }

        append(
            """
                const replaceComponent = (tag, component, callback) => {
                    for (const e of document.getElementsByTagName(tag)) {
                        if (e.children.length === 0) {
                            const template = document.createElement("template");
                            template.innerHTML = component;
                            
                            const newElem = template.content.firstElementChild.cloneNode(true);
                            e.replaceWith(newElem);
                            callback(newElem);
                        }
                    }
                };
            """.trimIndent()
        )

        components.forEach { (tag, content, callback) ->
            appendLine("const ${tag}Component = `${content.serializeAsComponent()}`;")
            appendLine("const ${tag}ComponentCallback = (e) => {")
            if (callback != null) {
                appendLine(callback)
            }
            appendLine("};")
        }

        appendLine("window.addEventListener(\"load\", () => {")

        components.forEach { (tag, _, _) ->
            appendLine("    replaceComponent(\"$tag\", ${tag}Component, ${tag}ComponentCallback);")
        }

        appendLine("});")
    }

    /**
     * Transforms HTML markup with all transformers in this generator.
     *
     * @return the transformed markup
     */
    fun String.transformHtml(): String = transformers.fold(this) { content0, transformer -> transformer.transformHtml(content0) }

    /**
     * Transforms a JS script with all transformers in this generator.
     *
     * @return the transformed script
     */
    fun String.transformJs(): String = transformers.fold(this) { content0, transformer -> transformer.transformJs(content0) }

    /**
     * Transforms a CSS stylesheet with all transformers in this generator.
     *
     * @return the transformed stylesheet
     */
    fun String.transformCss(): String = transformers.fold(this) { content0, transformer -> transformer.transformCss(content0) }
}
