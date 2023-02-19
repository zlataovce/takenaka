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
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.generator.common.AbstractGenerator
import me.kcra.takenaka.generator.web.components.footerComponent
import me.kcra.takenaka.generator.web.components.navComponent
import me.kcra.takenaka.generator.web.pages.classPage
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
    val index: ClassSearchIndex = emptyClassSearchIndex()
) : AbstractGenerator(workspace, versions, mappingWorkspace, contributorProvider) {
    /**
     * Launches the generator.
     */
    override fun generate() {
        val composite = workspace.asComposite()

        val styleSupplier = DefaultStyleSupplier()

        runBlocking {
            mappings.forEach { (version, tree) ->
                val versionWorkspace = composite.versioned(version)
                val friendlyNameRemapper = ElementRemapper(tree, ::getFriendlyDstName)

                tree.classes.forEach { klass ->
                    // skip mappings without modifiers, those weren't in the server JAR
                    if (klass.getName(VanillaMappingContributor.NS_MODIFIERS) == null) {
                        logger.warn { "Skipping generation for class ${getFriendlyDstName(klass)}, missing modifiers" }
                    } else {
                        launch(coroutineDispatcher) {
                            classPage(versionWorkspace, friendlyNameRemapper, index, styleSupplier::apply, klass)
                                .serialize(versionWorkspace, "${getFriendlyDstName(klass)}.html")
                        }
                    }
                }
            }
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
            mapOf(
                "nav" to makeBasicComponent { navComponent() },
                "footer" to makeBasicComponent { footerComponent() }
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
     * @param components map of tag names to the component
     * @return the content of the component file
     */
    fun generateComponentFile(components: Map<String, Document>): String = buildString {
        fun Document.serializeAsComponent(): String {
            var content = serialize(prettyPrint = false)
            transformers.forEach { transformer ->
                content = transformer.transformHtml(content)
            }

            return content.substringAfter("\n")
        }

        append(
            """
                const replaceComponent = (tag, component) => {
                    for (let e of document.getElementsByTagName(tag)) {
                        if (e.children.length === 0) {
                            e.outerHTML = component;
                        }
                    }
                };
            """.trimIndent()
        )

        components.forEach { (tag, document) ->
            append("const ${tag}Component = `${document.serializeAsComponent()}`;")
        }

        append("window.addEventListener(\"load\", () => {")

        components.forEach { (tag, _) ->
            append("    replaceComponent(\"$tag\", ${tag}Component);")
        }

        append("});")
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

    /**
     * A synchronized [StyleSupplier] implementation.
     */
    private class DefaultStyleSupplier(val styles: MutableMap<String, String> = mutableMapOf()) {
        private val supplierLock: Lock = ReentrantLock()

        fun apply(k: String, s: String): String = supplierLock.withLock {
            styles.putIfAbsent(k, s); k
        }
    }
}

/**
 * A function that provides a CSS class name from a key and the style content.
 */
typealias StyleSupplier = (String, String) -> String

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
