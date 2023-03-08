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
import me.kcra.takenaka.core.mapping.adapter.completeInnerClassNames
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.modifiers
import me.kcra.takenaka.generator.common.AbstractGenerator
import me.kcra.takenaka.generator.common.ContributorProvider
import me.kcra.takenaka.generator.web.components.footerComponent
import me.kcra.takenaka.generator.web.components.navComponent
import me.kcra.takenaka.generator.web.pages.*
import me.kcra.takenaka.generator.web.transformers.Transformer
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.w3c.dom.Document
import java.io.BufferedReader
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
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
 * @param skipSynthetics whether synthetic classes and their members should be skipped
 * @param transformers a list of transformers that transform the output
 * @param namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @param namespaceBadgeColors a map of namespaces and their colors, defaults to #94a3b8 for all of them
 * @param namespaceFriendlyNames a map of namespaces and their names that will be shown in the documentation, unspecified namespaces will not be shown
 * @param namespaceDefaultBadgeColor the default namespace badge color, which will be used if not specified in [namespaceBadgeColors]
 * @param index a resolver for foreign class references
 * @param spigotLikeNamespaces the namespaces that should have [replaceCraftBukkitNMSVersion] and [completeInnerClassNames] applied
 * @param licenseMetaKeys a map of namespaces and their license metadata keys
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
    val namespaceDefaultBadgeColor: String = "#94a3b8",
    val index: ClassSearchIndex = emptyClassSearchIndex(),
    val spigotLikeNamespaces: List<String> = emptyList(),
    val licenseMetaKeys: Map<String, LicenseReference> = emptyMap()
) : AbstractGenerator(workspace, versions, coroutineDispatcher, skipSynthetics, mappingWorkspace, contributorProvider) {
    /**
     * Launches the generator.
     */
    override fun generate() {
        val composite: CompositeWorkspace by workspace
        val styleSupplier = DefaultStyleSupplier()

        generationContext(styleSupplier = styleSupplier::apply) {
            mappings.forEach { (version, tree) ->
                launch(coroutineDispatcher) {
                    spigotLikeNamespaces.forEach { ns ->
                        if (tree.getNamespaceId(ns) != MappingTree.NULL_NAMESPACE_ID) {
                            tree.replaceCraftBukkitNMSVersion(ns)
                            tree.completeInnerClassNames(ns)
                        }
                    }
                    val versionWorkspace by composite.createVersioned {
                        this.version = version
                    }

                    val friendlyNameRemapper = ElementRemapper(tree, ::getFriendlyDstName)
                    val classMap = mutableMapOf<String, MutableMap<String, ClassType>>()

                    // class index format, similar to a CSV:
                    // first line is a "header", this is a tab-delimited string with friendly namespace names + its badge colors, which are delimited by a colon ("namespace:#color")
                    // following lines are tab-separated strings with the mappings, each substring belongs to its column (header.split("\t").get(substringIndex) -> "namespace:#color")

                    // example:
                    // Obfuscated:#581C87   Mojang:#4D7C0F  Intermediary:#0369A1
                    // a	com/mojang/math/Matrix3f	net/minecraft/class_4581
                    // b	com/mojang/math/Matrix4f	net/minecraft/class_1159
                    // ... (repeats like this for all classes)
                    val classIndex = buildString {
                        val namespaces = (0 until tree.maxNamespaceId)
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

                            appendLine(namespaces.values.joinToString("\t") { klass.getName(it) ?: "" })
                        }
                    }

                    classMap.forEach { (packageName, classes) ->
                        packagePage(versionWorkspace, packageName, classes)
                            .serialize(versionWorkspace, "$packageName/index.html")
                    }

                    val licenses = licenseMetaKeys
                        .mapNotNull { (ns, ref) ->
                            val content = tree.getMetadata(ref.content) ?: return@mapNotNull null
                            val source = tree.getMetadata(ref.source) ?: return@mapNotNull null

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

            versionsPage(mappings.entries.associate { (version, tree) -> version to tree.dstNamespaces })
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
                // dynamically find the version in the URL, this is a hack, I know
                callback = """
                    const overviewLink = document.getElementById("overview-link");
                    const licensesLink = document.getElementById("licenses-link");
                    const searchInput = document.getElementById("search-input");
                    
                    const path = window.location.pathname.substring(1);
                    if (path) {
                        const parts = [];
                        for (const part of path.split("/")) {
                            parts.push(part);
                            if (!part.endsWith(".html") && part.includes(".")) {
                                const baseUrl = "/" + parts.join("/");
                                
                                overviewLink.href = baseUrl + "/index.html";
                                licensesLink.href = baseUrl + "/licenses.html";
                                searchInput.addEventListener("input", (evt) => search(baseUrl, evt.target.value));
                                return;
                            }
                        }
                    }
                
                    overviewLink.remove();
                    licensesLink.remove();
                    searchInput.remove();
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
            file.writer().use { it.write(this, prettyPrint = false) }
        } else {
            file.writeText(serialize(prettyPrint = false).transformHtml())
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
                            e.outerHTML = component;
                            callback(e);
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
     * Gets a CSS color of the supplied namespace.
     *
     * @param ns the namespace
     * @return the color
     */
    fun getNamespaceBadgeColor(ns: String) = generator.namespaceBadgeColors[ns] ?: generator.namespaceDefaultBadgeColor

    /**
     * Formats a modifier integer into a string.
     *
     * @param mod the modifier integer
     * @param mask the modifier mask (you can get that from the [Modifier] class or use 0)
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
        // an enum is implicitly final
        // we need to check the unmasked modifiers here, since ACC_ENUM is not among Modifier#classModifiers
        if ((mMod and Opcodes.ACC_FINAL) != 0 && (mask != Modifier.classModifiers() || (mod and Opcodes.ACC_ENUM) == 0)) append("final ")
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

/**
 * A license metadata key pair.
 *
 * @property content the license content metadata key
 * @property source the license source metadata key
 */
data class LicenseReference(val content: String, val source: String)

/**
 * Creates a new license metadata key pair.
 *
 * @param content the license content metadata key
 * @param source the license source metadata key
 * @return the license metadata key pair
 */
fun licenseReferenceOf(content: String, source: String) = LicenseReference(content, source)

/**
 * A license declaration.
 *
 * @property content the license content
 * @property source the license source, probably a link
 */
data class License(val content: String, val source: String)

/**
 * Creates a new license declaration.
 *
 * @param content the license content
 * @param source the license source, probably a link
 * @return the license declaration
 */
fun licenseOf(content: String, source: String) = License(content, source)
