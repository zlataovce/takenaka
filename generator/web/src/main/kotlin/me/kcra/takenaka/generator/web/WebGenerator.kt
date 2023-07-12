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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.html.dom.serialize
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.core.mapping.resolve.impl.craftBukkitNmsVersion
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.core.mapping.util.allNamespaceIds
import me.kcra.takenaka.core.mapping.util.hash
import me.kcra.takenaka.generator.common.Generator
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import me.kcra.takenaka.generator.common.provider.MappingProvider
import me.kcra.takenaka.generator.web.components.footerComponent
import me.kcra.takenaka.generator.web.components.navComponent
import me.kcra.takenaka.generator.web.pages.*
import me.kcra.takenaka.generator.web.transformers.MinifyingTransformer
import me.kcra.takenaka.generator.web.transformers.Transformer
import net.fabricmc.mappingio.tree.MappingTreeView
import org.w3c.dom.Document
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.appendText
import kotlin.io.path.writeText

/**
 * A generator that generates documentation in an HTML format.
 *
 * An instance can be reused, but it is **not** thread-safe!
 *
 * @property workspace the workspace in which this generator can move around
 * @property config the website generation configuration
 * @author Matouš Kučera
 */
class WebGenerator(override val workspace: Workspace, val config: WebConfiguration) : Generator, Transformer {
    private val namespaceFriendlyNames = config.namespaces.mapValues { it.value.friendlyName }
    private val currentComposite by workspace

    internal val hasMinifier = config.transformers.any { it is MinifyingTransformer }

    /**
     * A [Comparator] for comparing the friendliness of namespaces, useful for sorting.
     */
    val friendlinessComparator = compareBy(config.namespaceFriendlinessIndex::indexOf)

    /**
     * The "history" folder.
     */
    val historyWorkspace = currentComposite.createWorkspace {
        name = "history"
    }

    /**
     * The "assets" folder.
     */
    val assetWorkspace = currentComposite.createWorkspace {
        name = "assets"
    }

    /**
     * A convenience index for looking up namespaces by their friendly names.
     */
    val namespacesByFriendlyNames = config.namespaces.mapKeys { it.value.friendlyName }

    /**
     * Launches the generator with mappings provided by the provider.
     *
     * @param mappingProvider the mapping provider
     * @param ancestryProvider the ancestry provider
     */
    override suspend fun generate(mappingProvider: MappingProvider, ancestryProvider: AncestryProvider) {
        val mappings = mappingProvider.get()
        val tree = ancestryProvider.klass<MappingTreeView, MappingTreeView.ClassMappingView>(mappings)

        val styleProvider: StyleProvider? = if (config.emitPseudoElements) StyleProviderImpl() else null
        generationContext(ancestryProvider, styleProvider) {
            // used for looking up history hashes - for linking
            val hashMap = IdentityHashMap<MappingTreeView.ClassMappingView, String>()

            tree.forEach { node ->
                val (_, firstKlass) = node.first
                val fileHash = firstKlass.hash.take(10)

                node.forEach { (_, klass) ->
                    hashMap[klass] = fileHash
                }

                launch(Dispatchers.Default + CoroutineName("page-coro")) {
                    historyPage(node)
                        .serialize(historyWorkspace, "$fileHash.html")
                }
            }

            mappings.forEach { (version, tree) ->
                val nmsVersion = tree.craftBukkitNmsVersion

                launch(Dispatchers.Default + CoroutineName("generate-coro")) {
                    val versionWorkspace = currentComposite.createVersionedWorkspace {
                        this.version = version
                    }

                    val friendlyNameRemapper = ElementRemapper(tree, ::getFriendlyDstName)
                    val classMap = sortedMapOf<String, MutableMap<ClassType, MutableSet<String>>>()

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
                            .sortedBy { config.namespaceFriendlinessIndex.indexOf(it.first) }
                            .toMap()

                        appendLine(namespaces.keys.joinToString("\t") { "${namespaceFriendlyNames[it]}:${getNamespaceBadgeColor(it)}" })

                        tree.classes.forEach { klass ->
                            val friendlyName = getFriendlyDstName(klass)

                            launch(Dispatchers.Default + CoroutineName("page-coro")) {
                                classPage(klass, hashMap[klass], nmsVersion, versionWorkspace, friendlyNameRemapper)
                                    .serialize(versionWorkspace, "$friendlyName.html")
                            }

                            classMap.getOrPut(friendlyName.substringBeforeLast('/')) { sortedMapOf(compareBy(ClassType::ordinal)) }
                                .getOrPut(classTypeOf(klass.modifiers), ::sortedSetOf) += friendlyName.substringAfterLast('/')

                            appendLine(namespaces.values.joinToString("\t") { nsId ->
                                val namespacedNmsVersion = if (tree.getNamespaceName(nsId) in versionReplaceCandidates) nmsVersion else null

                                klass.getName(nsId)?.replaceCraftBukkitNMSVersion(namespacedNmsVersion) ?: ""
                            })
                        }
                    }.replace("net/minecraft", "%nm").replace("com/mojang", "%cm")

                    classMap.forEach { (packageName, classes) ->
                        packagePage(versionWorkspace, packageName, classes)
                            .serialize(versionWorkspace, "$packageName/index.html")
                    }

                    val licenses = config.namespaces
                        .mapNotNull { (ns, nsDesc) ->
                            if (nsDesc.license == null) return@mapNotNull null

                            val content = nsDesc.license.content.let(tree::getMetadata) ?: return@mapNotNull null
                            val source = nsDesc.license.source.let(tree::getMetadata) ?: return@mapNotNull null

                            return@mapNotNull ns to License(content, source)
                        }
                        .toMap()

                    licensePage(versionWorkspace, licenses)
                        .serialize(versionWorkspace, "licenses.html")

                    overviewPage(versionWorkspace, classMap.keys)
                        .serialize(versionWorkspace, "index.html")

                    versionWorkspace["class-index.js"].writeText("updateClassIndex(`${classIndex.trim()}`);") // do not minify this file
                }
            }

            versionsPage(config.welcomeMessage, mappings.mapValues { it.value.dstNamespaces })
                .serialize(workspace, "index.html")
        }

        copyAsset("main.js")

        val componentFileContent = generateComponentScript(
            component {
                tag = "nav"
                content {
                    navComponent()
                }
                callback = """
                    const overviewLink = document.getElementById("overview-link");
                    const licensesLink = document.getElementById("licenses-link");
                    const searchInput = document.getElementById("search-input");
                    const searchBox = document.getElementById("search-box");
                    
                    if (baseUrl) {
                        overviewLink.href = `${'$'}{baseUrl}/index.html`;
                        licensesLink.href = `${'$'}{baseUrl}/licenses.html`;
                        
                        searchInput.addEventListener("input", (evt) => search(evt.target.value));
                        document.addEventListener("mouseup", (evt) => {
                            searchBox.style.display = !searchInput.contains(evt.target) && !searchBox.contains(evt.target) ? "none" : "block";
                        });
                        
                        initialIndexLoadPromise.then(updateOptions);
                    } else {
                        overviewLink.remove();
                        licensesLink.remove();
                        searchInput.remove();
                        searchBox.remove();
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
        assetWorkspace["main.js"].appendText(transformJs(componentFileContent))

        copyAsset("main.css") // main.css should be copied last to minify correctly
        styleProvider?.let { assetWorkspace["main.css"].appendText(transformCss(it.asStyleSheet())) }
    }

    /**
     * Copies and transforms an asset from `resources`, placing it into [assetWorkspace].
     *
     * @param name the asset path, **must not begin with a slash**
     */
    fun copyAsset(name: String) {
        val inputStream = checkNotNull(javaClass.getResourceAsStream("/assets/$name")) {
            "Could not copy over /assets/$name from resources"
        }

        val destination = assetWorkspace[name]
        if (config.transformers.isNotEmpty()) {
            val content = inputStream.bufferedReader().use(BufferedReader::readText)

            destination.writeText(
                when (name.substringAfterLast('.')) {
                    "css" -> transformCss(content)
                    "js" -> transformJs(content)
                    "html" -> transformHtml(content)
                    else -> content
                }
            )
        } else {
            inputStream.use { Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    /**
     * Generates script for loading components.
     *
     * @param components a list of tag-document-callback
     * @return the content of the component file
     */
    fun generateComponentScript(vararg components: ComponentDefinition): String = buildString {
        fun Document.serializeAsComponent(): String = transformHtml(
            serialize(prettyPrint = false)
                .substringAfter("\n") // remove <!DOCTYPE html>
        )

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
     * @param content the markup to transform
     * @return the transformed markup
     */
    override fun transformHtml(content: String): String = config.transformers.fold(content) { content0, transformer -> transformer.transformHtml(content0) }

    /**
     * Transforms a JS script with all transformers in this generator.
     *
     * @param content the script to transform
     * @return the transformed script
     */
    override fun transformJs(content: String): String = config.transformers.fold(content) { content0, transformer -> transformer.transformJs(content0) }

    /**
     * Transforms a CSS stylesheet with all transformers in this generator.
     *
     * @param content the stylesheet to transform
     * @return the transformed stylesheet
     */
    override fun transformCss(content: String): String = config.transformers.fold(content) { content0, transformer -> transformer.transformCss(content0) }
}
