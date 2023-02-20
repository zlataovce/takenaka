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
import kotlinx.coroutines.runBlocking
import kotlinx.html.BODY
import kotlinx.html.body
import kotlinx.html.consumers.filter
import kotlinx.html.dom.append
import kotlinx.html.dom.document
import kotlinx.html.dom.serialize
import kotlinx.html.dom.write
import kotlinx.html.html
import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ContributorProvider
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.generator.common.AbstractGenerator
import me.kcra.takenaka.generator.web.components.footerComponent
import me.kcra.takenaka.generator.web.components.navComponent
import me.kcra.takenaka.generator.web.pages.classPage
import me.kcra.takenaka.generator.web.pages.overviewPage
import me.kcra.takenaka.generator.web.pages.packagePage
import me.kcra.takenaka.generator.web.pages.versionsPage
import me.kcra.takenaka.generator.web.transformers.Transformer
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import org.w3c.dom.Document
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {}

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
 * @param transformers a list of transformers that transform the output
 * @param namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @param namespaceBadgeColors a map of namespaces and their colors, defaults to #94a3b8 for all of them
 * @param namespaceFriendlyNames a map of namespaces and their names that will be shown in the documentation, unspecified namespaces will not be shown
 * @param index a resolver for foreign class references
 * @param skipSynthetics whether synthetic classes and their members should be skipped
 * @author Matouš Kučera
 */
class WebGenerator(
    workspace: Workspace,
    versions: List<String>,
    mappingWorkspace: CompositeWorkspace,
    contributorProvider: ContributorProvider,
    val coroutineDispatcher: CoroutineContext = Dispatchers.IO,
    val transformers: List<Transformer> = emptyList(),
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val namespaceBadgeColors: Map<String, String> = emptyMap(),
    val namespaceFriendlyNames: Map<String, String> = emptyMap(),
    val index: ClassSearchIndex = emptyClassSearchIndex(),
    val skipSynthetics: Boolean = true
) : AbstractGenerator(workspace, versions, mappingWorkspace, contributorProvider) {
    /**
     * Launches the generator.
     */
    override fun generate() {
        val composite = workspace.asComposite()
        val styleSupplier = DefaultStyleSupplier()
        val context = GenerationContext(this, styleSupplier::apply)

        runBlocking {
            mappings.forEach { (version, tree) ->
                val versionWorkspace = composite.versioned(version)
                val friendlyNameRemapper = ElementRemapper(tree, context::getFriendlyDstName)

                val classMap = mutableMapOf<String, MutableMap<String, ClassType>>()

                tree.classes.forEach { klass ->
                    val mod = klass.getName(VanillaMappingContributor.NS_MODIFIERS)?.toIntOrNull()
                    val friendlyName = context.getFriendlyDstName(klass)

                    // skip mappings without modifiers, those weren't in the server JAR
                    if (mod == null) {
                        logger.warn { "Skipping generation for class $friendlyName, missing modifiers" }
                    } else if (skipSynthetics && (mod and Opcodes.ACC_SYNTHETIC) != 0) {
                        logger.warn { "Skipping generation for class $friendlyName, synthetic" }
                    } else {
                        launch(coroutineDispatcher) {
                            context.classPage(klass, versionWorkspace, friendlyNameRemapper)
                                .serialize(versionWorkspace, "$friendlyName.html")
                        }

                        classMap.getOrPut(friendlyName.substringBeforeLast('/')) { mutableMapOf() } +=
                            friendlyName.substringAfterLast('/') to classTypeOf(mod)
                    }
                }

                classMap.forEach { (packageName, classes) ->
                    context.packagePage(versionWorkspace, packageName, classes)
                        .serialize(versionWorkspace, "$packageName/index.html")
                }

                context.overviewPage(versionWorkspace, classMap.keys)
                    .serialize(versionWorkspace, "index.html")
            }

            context.versionsPage(mappings.entries.associate { (version, tree) -> version to tree.dstNamespaces })
                .serialize(workspace, "index.html")
        }

        workspace["assets"].mkdirs()

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
                inputStream.use {
                    Files.copy(it, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        copyAsset("main.js")

        fun makeBasicComponent(block: BODY.() -> Unit): Document = document {
            append.filter { if (it.tagName in listOf("html", "body")) SKIP else PASS }
                .html {
                    body {
                        block()
                    }
                }
        }

        var componentFileContent = generateComponentFile(
            listOf(
                Triple(
                    "nav",
                    makeBasicComponent { navComponent() },
                    // dynamically find the version in the URL, this is a hack, I know
                    """
                        const link = document.getElementById("overview-link");
                        const path = window.location.pathname.substring(1);
                        if (path) {
                            const parts = [];
                            for (const part of path.split("/")) {
                                parts.push(part);
                                if (part.includes(".")) {
                                    link.href = "/" + parts.join("/") + "/index.html";
                                    return;
                                }
                            }
                        }
                    
                        link.remove();
                    """.trimIndent()
                ),
                Triple("footer", makeBasicComponent { footerComponent() }, null)
            )
        )
        transformers.forEach { transformer ->
            componentFileContent = transformer.transformJs(componentFileContent)
        }

        workspace["assets/components.js"].writeText(componentFileContent)

        var generatedStylesContent = ""
        styleSupplier.styles.forEach { (k, s) ->
            generatedStylesContent += """
                .$k {
                    $s
                }
            """.trimIndent()
        }
        transformers.forEach { transformer ->
            generatedStylesContent = transformer.transformCss(generatedStylesContent)
        }

        workspace["assets/generated.css"].writeText(generatedStylesContent)

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
        file.parentFile.mkdirs()

        if (transformers.isEmpty()) {
            file.writer().use { it.write(this, prettyPrint = false) }
        } else {
            var content = serialize(prettyPrint = false)
            transformers.forEach { transformer ->
                content = transformer.transformHtml(content)
            }

            file.writeText(content)
        }
    }

    /**
     * Generates a components.js file.
     *
     * @param components a list of tag-document-callback
     * @return the content of the component file
     */
    fun generateComponentFile(components: List<Triple<String, Document, String?>>): String = buildString {
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
                    for (let e of document.getElementsByTagName(tag)) {
                        if (e.children.length === 0) {
                            e.outerHTML = component;
                            callback(e);
                        }
                    }
                };
            """.trimIndent()
        )

        components.forEach { (tag, document, callback) ->
            append("const ${tag}Component = `${document.serializeAsComponent()}`;")
            append("const ${tag}ComponentCallback = (e) => {")
            if (callback != null) {
                append(callback)
            }
            append("};")
        }

        append("window.addEventListener(\"load\", () => {")

        components.forEach { (tag, _) ->
            append("    replaceComponent(\"$tag\", ${tag}Component, ${tag}ComponentCallback);")
        }

        append("});")
    }
}

/**
 * A generation context.
 *
 * @author Matouš Kučera
 */
class GenerationContext(val generator: WebGenerator, val styleSupplier: StyleSupplier) {
    val index: ClassSearchIndex by generator::index

    /**
     * Gets a "friendly" destination name of an element.
     *
     * @param elem the element
     * @return the name
     */
    fun getFriendlyDstName(elem: MappingTree.ElementMapping): String {
        generator.namespaceFriendlinessIndex.forEach { ns ->
            elem.getName(ns)?.let { return it }
        }
        return (0 until elem.tree.maxNamespaceId).firstNotNullOfOrNull(elem::getDstName) ?: elem.srcName
    }

    /**
     * Formats a modifier integer into a string.
     *
     * @param mod the modifier integer
     * @param mask the modifier mask (you can get that from the [java.lang.reflect.Modifier] class or use 0)
     * @return the modifier string
     */
    fun formatModifiers(mod: Int, mask: Int): String = buildString {
        val mMod = mod and mask

        if ((mMod and Opcodes.ACC_PUBLIC) != 0) append("public ")
        if ((mMod and Opcodes.ACC_PRIVATE) != 0) append("private ")
        if ((mMod and Opcodes.ACC_PROTECTED) != 0) append("protected ")
        if ((mMod and Opcodes.ACC_STATIC) != 0) append("static ")
        // an interface is implicitly abstract
        // we need to check the unmasked modifiers here, since ACC_INTERFACE is not among Modifier#classModifiers
        if ((mMod and Opcodes.ACC_ABSTRACT) != 0 && (mod and Opcodes.ACC_INTERFACE) == 0) append("abstract ")
        if ((mMod and Opcodes.ACC_FINAL) != 0) append("final ")
        if ((mMod and Opcodes.ACC_NATIVE) != 0) append("native ")
        if ((mMod and Opcodes.ACC_STRICT) != 0) append("strict ")
        if ((mMod and Opcodes.ACC_SYNCHRONIZED) != 0) append("synchronized ")
        if ((mMod and Opcodes.ACC_TRANSIENT) != 0) append("transient ")
        if ((mMod and Opcodes.ACC_VOLATILE) != 0) append("volatile ")
    }
}

/**
 * A function that provides a CSS class name from a key and the style content.
 */
typealias StyleSupplier = (String, String) -> String

/**
 * A synchronized [StyleSupplier] implementation.
 */
class DefaultStyleSupplier(val styles: MutableMap<String, String> = mutableMapOf()) {
    private val supplierLock: Lock = ReentrantLock()

    fun apply(k: String, s: String): String = supplierLock.withLock {
        styles.putIfAbsent(k, s); k
    }
}

/**
 * Replaces dots with slashes (e.g. qualified class name to internal name).
 *
 * @return the replaced string
 */
fun String.toInternalName(): String = replace('.', '/')

/**
 * Replaces slashes with dots (e.g. internal name to qualified class name).
 *
 * @return the replaced string
 */
fun String.fromInternalName(): String = replace('/', '.')
