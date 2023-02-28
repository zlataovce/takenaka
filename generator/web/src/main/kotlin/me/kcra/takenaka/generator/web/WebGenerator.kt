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
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.modifiers
import me.kcra.takenaka.generator.common.AbstractGenerator
import me.kcra.takenaka.generator.common.ContributorProvider
import me.kcra.takenaka.generator.web.components.footerComponent
import me.kcra.takenaka.generator.web.components.navComponent
import me.kcra.takenaka.generator.web.pages.classPage
import me.kcra.takenaka.generator.web.pages.overviewPage
import me.kcra.takenaka.generator.web.pages.packagePage
import me.kcra.takenaka.generator.web.pages.versionsPage
import me.kcra.takenaka.generator.web.transformers.Transformer
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.w3c.dom.Document
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

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
 * @param skipSynthetics whether synthetic classes and their members should be skipped
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
    coroutineDispatcher: CoroutineContext,
    skipSynthetics: Boolean,
    val transformers: List<Transformer> = emptyList(),
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val namespaceBadgeColors: Map<String, String> = emptyMap(),
    val namespaceFriendlyNames: Map<String, String> = emptyMap(),
    val index: ClassSearchIndex = emptyClassSearchIndex()
) : AbstractGenerator(workspace, versions, coroutineDispatcher, skipSynthetics, mappingWorkspace, contributorProvider) {
    /**
     * Launches the generator.
     */
    override fun generate() {
        val composite: CompositeWorkspace by workspace
        val styleSupplier = DefaultStyleSupplier()

        generationContext(styleSupplier = styleSupplier::apply) {
            mappings.forEach { (version, tree) ->
                tree.replaceCraftBukkitNMSVersion()
                val versionWorkspace by composite.versioned {
                    this.version = version
                }

                val friendlyNameRemapper = ElementRemapper(tree, this::getFriendlyDstName)
                val classMap = mutableMapOf<String, MutableMap<String, ClassType>>()

                tree.classes.forEach { klass ->
                    val friendlyName = getFriendlyDstName(klass)

                    launch(coroutineDispatcher) {
                        classPage(klass, versionWorkspace, friendlyNameRemapper)
                            .serialize(versionWorkspace, "$friendlyName.html")
                    }

                    classMap.getOrPut(friendlyName.substringBeforeLast('/')) { mutableMapOf() } +=
                        friendlyName.substringAfterLast('/') to classTypeOf(klass.modifiers)
                }

                classMap.forEach { (packageName, classes) ->
                    packagePage(versionWorkspace, packageName, classes)
                        .serialize(versionWorkspace, "$packageName/index.html")
                }

                overviewPage(versionWorkspace, classMap.keys)
                    .serialize(versionWorkspace, "index.html")
            }

            versionsPage(mappings.entries.associate { (version, tree) -> version to tree.dstNamespaces })
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

        val componentFileContent = generateComponentFile(
            component {
                tag = "nav"
                content {
                    navComponent()
                }
                // dynamically find the version in the URL, this is a hack, I know
                callback = """
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
                    for (let e of document.getElementsByTagName(tag)) {
                        if (e.children.length === 0) {
                            e.outerHTML = component;
                            callback(e);
                        }
                    }
                };
            """.trimIndent()
        )

        components.forEach { (tag, content, callback) ->
            append("const ${tag}Component = `${content.serializeAsComponent()}`;")
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

/**
 * Opens a generation context.
 *
 * @param styleSupplier the style supplier that will be used in the context
 * @param block the context user
 */
inline fun <R> WebGenerator.generationContext(noinline styleSupplier: StyleSupplier, crossinline block: suspend GenerationContext.() -> R): R =
    runBlocking(coroutineDispatcher) { block(GenerationContext(this, this@generationContext, styleSupplier)) }

/**
 * A generation context.
 *
 * @author Matouš Kučera
 */
class GenerationContext(coroutineScope: CoroutineScope, val generator: WebGenerator, val styleSupplier: StyleSupplier) : CoroutineScope by coroutineScope {
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
 * Remaps a type and creates a link if a mapping has been found.
 *
 * @param version the mapping version
 * @param internalName the internal name of the class to be remapped
 * @param packageIndex the index used for looking up foreign class references
 * @return the remapped type, a link if it was found
 */
fun Remapper.mapTypeAndLink(version: Version, internalName: String, packageIndex: ClassSearchIndex, linkRemapper: Remapper? = null): String {
    val foreignUrl = packageIndex.linkClass(internalName)
    if (foreignUrl != null) {
        return """<a href="$foreignUrl">${internalName.substringAfterLast('/')}</a>"""
    }

    val remappedName = mapType(internalName)
    val linkName = linkRemapper?.mapType(internalName) ?: remappedName

    return if (remappedName != internalName || linkName != remappedName) {
        """<a href="/${version.id}/$linkName.html">${remappedName.substringAfterLast('/')}</a>"""
    } else {
        remappedName.fromInternalName()
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

    /**
     * The [StyleSupplier].
     */
    fun apply(k: String, s: String): String = supplierLock.withLock {
        styles.putIfAbsent(k, s); k
    }

    /**
     * Generates a stylesheet from gathered classes.
     *
     * @return the stylesheet content
     */
    fun generateStyleSheet(): String = buildString {
        styles.forEach { (k, s) ->
            append(
                """
                    .$k {
                        $s
                    }
                """.trimIndent()
            )
        }
    }
}

/**
 * A HTML component.
 *
 * @property tag the main tag of the component, any tag that is empty and has the same name, will be replaced with the component
 * @property content the component content/body
 * @property callback a raw JS script that is called after the component has been loaded onto the site, an `e` variable of type `HTMLElement` is available
 */
data class ComponentDefinition(
    val tag: String,
    val content: Document,
    val callback: String?
)

/**
 * Builds an HTML component.
 *
 * @param block the builder action
 * @return the component
 */
inline fun component(block: MutableComponentDefinition.() -> Unit): ComponentDefinition = MutableComponentDefinition().apply(block).toComponent()

/**
 * An HTML component builder.
 *
 * @property tag the main tag of the component, any tag that is empty and has the same name, will be replaced with the component
 * @property content the component content/body
 * @property callback a raw JS script that is called after the component has been loaded onto the site, an `e` variable of type `HTMLElement` is available
 */
class MutableComponentDefinition {
    lateinit var tag: String
    lateinit var content: Document
    var callback: String? = null

    /**
     * Builds and sets the content of this component.
     *
     * @param block the builder action
     */
    inline fun content(crossinline block: BODY.() -> Unit) {
        content = document {
            append.filter { if (it.tagName in listOf("html", "body")) SKIP else PASS }
                .html {
                    body {
                        block()
                    }
                }
        }
    }

    /**
     * Creates an immutable component out of this builder.
     *
     * @return the component
     */
    fun toComponent() = ComponentDefinition(tag, content, callback)
}
