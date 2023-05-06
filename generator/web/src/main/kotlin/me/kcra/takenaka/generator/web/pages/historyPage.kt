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

package me.kcra.takenaka.generator.web.pages

import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.allNamespaceIds
import me.kcra.takenaka.core.mapping.ancestry.*
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTreeView.*
import org.w3c.dom.Document
import java.util.*

/**
 * Generates a class history page.
 *
 * @param node the ancestry node
 * @return the generated document
 */
fun GenerationContext.historyPage(node: ClassAncestryNode): Document = createHTMLDocument().html {
    val lastFriendlyMapping = getFriendlyDstName(node.last.value).fromInternalName()

    val classNameRows = buildDiff {
        node.forEach { (version, klass) ->
            klass.tree.allNamespaceIds.forEach { id ->
                val ns = klass.tree.getNamespaceName(id)

                val nsFriendlyName = getNamespaceFriendlyName(ns)
                if (nsFriendlyName != null) {
                    append(version, nsFriendlyName, klass.getName(id)?.fromInternalName())
                }
            }
        }
    }

    val fieldTree = fieldAncestryTreeOf(node)
    val fieldRows = buildFieldDiff {
        node.keys.forEach { version ->
            val tree = fieldTree.trees[version] ?: error("Field tree does not have parent's version")
            val friendlyNameRemapper = ElementRemapper(tree, ::getFriendlyDstName)

            fieldTree.forEach { fieldNode ->
                val nullableField = fieldNode[version]

                append(
                    version,
                    fieldNode,
                    nullableField?.let { field ->
                        HistoricalDescriptableDetail(
                            formatFieldDescriptor(field, version, friendlyNameRemapper),
                            tree.allNamespaceIds
                                .mapNotNull { id ->
                                    id to (getNamespaceFriendlyName(tree.getNamespaceName(id)) ?: return@mapNotNull null)
                                }
                                .sortedBy { generator.config.namespaceFriendlinessIndex.indexOf(it.second) }
                                .mapNotNull { (id, ns) ->
                                    field.getName(id)?.let { name ->
                                        textBadgeComponentUnsafe(ns, getFriendlyNamespaceBadgeColor(ns), styleConsumer) + name
                                    }
                                }
                                .joinToString()
                        )
                    }
                )
            }
        }
    }

    val methodTree = methodAncestryTreeOf(node)
    val methodRows = buildMethodDiff {
        node.keys.forEach { version ->
            val tree = methodTree.trees[version] ?: error("Method tree does not have parent's version")
            val friendlyNameRemapper = ElementRemapper(tree, ::getFriendlyDstName)

            methodTree.forEach { methodNode ->
                val nullableMethod = methodNode[version]

                append(
                    version,
                    methodNode,
                    nullableMethod?.let { method ->
                        val methodDeclaration = formatMethodDescriptor(
                            method,
                            method.modifiers,
                            version,
                            friendlyNameRemapper,
                            generateNamedParameters = false
                        )

                        HistoricalDescriptableDetail(
                            buildString {
                                methodDeclaration.formals?.let { append(it).append(' ') }
                                append(methodDeclaration.returnType)
                                append(' ')
                                append(methodDeclaration.args)
                                methodDeclaration.exceptions
                                    ?.let { append(" throws $it") }
                            },
                            tree.allNamespaceIds
                                .mapNotNull { id ->
                                    id to (getNamespaceFriendlyName(tree.getNamespaceName(id)) ?: return@mapNotNull null)
                                }
                                .sortedBy { generator.config.namespaceFriendlinessIndex.indexOf(it.second) }
                                .mapNotNull { (id, ns) ->
                                    method.getName(id)?.let { name ->
                                        textBadgeComponentUnsafe(ns, getFriendlyNamespaceBadgeColor(ns), styleConsumer) + name
                                    }
                                }
                                .joinToString()
                        )
                    }
                )
            }
        }
    }

    val constructorTree = methodAncestryTreeOf(node, constructorMode = ConstructorComputationMode.ONLY)
    val constructorRows = buildMethodDiff {
        node.keys.forEach { version ->
            val tree = methodTree.trees[version] ?: error("Constructor tree does not have parent's version")
            val friendlyNameRemapper = ElementRemapper(tree, ::getFriendlyDstName)

            constructorTree.forEach { ctorNode ->
                val nullableMethod = ctorNode[version]

                append(
                    version,
                    ctorNode,
                    nullableMethod?.let { method ->
                        val methodDeclaration = formatMethodDescriptor(
                            method,
                            method.modifiers,
                            version,
                            friendlyNameRemapper,
                            generateNamedParameters = false
                        )

                        HistoricalDescriptableDetail(
                            buildString {
                                methodDeclaration.formals?.let { append(it).append(' ') }
                                append(methodDeclaration.args)
                                methodDeclaration.exceptions
                                    ?.let { append(" throws $it") }
                            },
                            // hack the diff builder for code reuse
                            "<init>"
                        )
                    }
                )
            }
        }
    }

    head {
        defaultResourcesComponent()
        if (generator.config.emitMetaTags) {
            metadataComponent(
                title = lastFriendlyMapping,
                description = if (node.size == 1) "1 version, ${node.first.key.id}" else "${node.size} versions, ${node.first.key.id} - ${node.last.key.id}",
                themeColor = "#21ff21"
            )
        }
        title(content = "history - $lastFriendlyMapping")
    }
    body {
        navPlaceholderComponent()
        main {
            h1 {
                +"History - $lastFriendlyMapping"
            }
            spacerBottomComponent()

            classNameRows.entries.forEachIndexed { i, (version, rows) ->
                val klass = node[version] ?: error("Could not resolve ${version.id} mapping of $lastFriendlyMapping")

                h3 {
                    a(href = "/${version.id}/${getFriendlyDstName(klass)}.html") {
                        +version.id
                    }
                }
                if (i == (classNameRows.size - 1)) { // is last (oldest)?
                    p(classes = "diff-status diff-first-occurrence")
                }
                p(classes = "diff-title") {
                    +"Names"
                }
                if (rows.isNotEmpty()) {
                    rows.forEach { row ->
                        p(classes = "diff-${row.type}") {
                            textBadgeComponent(row.key, getFriendlyNamespaceBadgeColor(row.key), styleConsumer)
                            +row.value
                        }
                    }
                } else {
                    p(classes = "diff-status diff-no-changes")
                }

                val versionFieldRows = fieldRows[version] ?: emptyList()

                p(classes = "diff-title") {
                    +"Fields"
                }
                if (versionFieldRows.isNotEmpty()) {
                    versionFieldRows.forEach { row ->
                        p(classes = "diff-${row.type}") {
                            unsafe {
                                +row.value.toString()
                            }
                        }
                    }
                } else {
                    p(classes = "diff-status diff-no-changes")
                }

                val versionCtorRows = constructorRows[version] ?: emptyList()

                p(classes = "diff-title") {
                    +"Constructors"
                }
                if (versionCtorRows.isNotEmpty()) {
                    versionCtorRows.forEach { row ->
                        p(classes = "diff-${row.type}") {
                            unsafe {
                                +row.value.descriptor
                            }
                        }
                    }
                } else {
                    p(classes = "diff-status diff-no-changes")
                }

                val versionMethodRows = methodRows[version] ?: emptyList()

                p(classes = "diff-title") {
                    +"Methods"
                }
                if (versionMethodRows.isNotEmpty()) {
                    versionMethodRows.forEach { row ->
                        p(classes = "diff-${row.type}") {
                            unsafe {
                                +row.value.toString()
                            }
                        }
                    }
                } else {
                    p(classes = "diff-status diff-no-changes")
                }

                if (i != (classNameRows.size - 1)) {
                    spacerYComponent()
                }
            }
        }
        footerPlaceholderComponent()
    }
}

/**
 * A historical detail of a descriptable class member (such as a field or a method), as displayed on the documentation.
 *
 * **A detail is equal if just the mappings match.**
 *
 * @property descriptor a textified descriptor/generic signature
 * @property mappings sorted and arranged mapping pairs
 */
data class HistoricalDescriptableDetail(
    val descriptor: String,
    val mappings: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HistoricalDescriptableDetail

        return mappings == other.mappings
    }
    override fun hashCode(): Int = mappings.hashCode()
    override fun toString(): String = "$descriptor: $mappings"
}

/**
 * A generic comparator.
 *
 * @param reverseOrder whether a reverse order [Comparator] should be used for sorting [rows]
 * @param K the key type
 * @param V the value type
 * @author Matouš Kučera
 */
class DiffBuilder<K, V>(reverseOrder: Boolean = true) {
    /**
     * A temporary store of previously appended values, used for differentiation.
     */
    private val values = mutableMapOf<K, V>()

    /**
     * Versions which had appended values to this builder.
     */
    val versions = mutableSetOf<Version>()

    /**
     * The differences, grouped and sorted by version for convenience.
     *
     * If a reverse order [Comparator] is applied, this is sorted newest to oldest.
     */
    val rows: SortedMap<Version, MutableList<DiffRow<K, V>>> = TreeMap(
        if (reverseOrder) Collections.reverseOrder() else null
    )

    /**
     * Makes all appended versions have an entry in [rows], empty or not.
     */
    fun addRowEntries() {
        versions.forEach { rows.getOrPut(it, ::mutableListOf) }
    }

    /**
     * Checks differences against formerly appended versions of the [value] under [key] and appends corresponding difference rows to the builder.
     *
     * If:
     *  - both the former value and are equal ([Object.equals]; null is a legal value), no rows are appended (only the value is updated in [values]).
     *  - the former value is null (not present) and [value] is non-null, a [DiffType.ADDITION] row is appended.
     *  - the former value is non-null and [value] is null, a [DiffType.DELETION] row is appended.
     *  - both the former value and [value] are non-null and are not equal ([Object.equals]),
     *    rows with both types will be appended (former value row is a [DiffType.DELETION], [value] row is an [DiffType.ADDITION]).
     *
     * @param version the version of the current [value]
     * @param key the cross-version key, most likely a namespace
     * @param value the version-dependent value, most likely a mapping
     */
    fun append(version: Version, key: K, value: V?) {
        versions += version

        val oldValue = values[key]
        if (oldValue != value) {
            val versionRows by lazy(LazyThreadSafetyMode.NONE) { rows.getOrPut(version, ::mutableListOf) }

            if (oldValue != null) {
                versionRows += DiffRow(DiffType.DELETION, version, key, oldValue)
            }
            if (value != null) {
                versionRows += DiffRow(DiffType.ADDITION, version, key, value)
            }
        }

        if (value == null) {
            values.remove(key)
        } else {
            values[key] = value
        }
    }
}

/**
 * A single change which happened in [version].
 *
 * Examples:
 *  - Intermediary mappings were added in 1.14, class mapping example: type = [DiffType.ADDITION], version = 1.14, key = Intermediary, value = net.minecraft.class_1234
 *  - a mapping was changed, class mapping example (changes must be done in two rows, addition and deletion):
 *    - type = [DiffType.DELETION], version = 1.17, key = Spigot, value = net.minecraft.server.${V}.MinecraftKey
 *    - type = [DiffType.ADDITION], version = 1.17, key = Spigot, value = net.minecraft.resources.MinecraftKey
 *
 * @property type the difference type, an addition or a deletion
 * @property version the version in which this difference is present
 * @property key the row key, most likely a namespace
 * @property value the row value, most likely a mapping (or multiple)
 * @param K the row key type
 * @param V the row value type
 */
data class DiffRow<K, V>(val type: DiffType, val version: Version, val key: K, val value: V)

/**
 * A type of difference.
 */
enum class DiffType {
    DELETION,
    ADDITION;

    override fun toString(): String = name.lowercase()
}

typealias StringDiffBuilder = DiffBuilder<String, String>
typealias StringDiffRow = DiffRow<String, String>

/**
 * Builds a simple string-string diff.
 *
 * @param reverseOrder whether a reverse order [Comparator] should be used on [DiffBuilder.rows]
 * @param block the builder action
 * @return the differences, grouped by version for convenience
 */
inline fun buildDiff(reverseOrder: Boolean = true, block: StringDiffBuilder.() -> Unit): Map<Version, List<StringDiffRow>> =
    StringDiffBuilder(reverseOrder).apply(block).apply(StringDiffBuilder::addRowEntries).rows

typealias DescriptableDiffBuilder<T> = DiffBuilder<AncestryTree.Node<T>, HistoricalDescriptableDetail>
typealias DescriptableDiffRow<T> = DiffRow<AncestryTree.Node<T>, HistoricalDescriptableDetail>

/**
 * Builds a simple field diff.
 *
 * @param reverseOrder whether a reverse order [Comparator] should be used on [DiffBuilder.rows]
 * @param block the builder action
 * @return the differences, grouped by version for convenience
 */
inline fun buildFieldDiff(reverseOrder: Boolean = true, block: DescriptableDiffBuilder<FieldMappingView>.() -> Unit): Map<Version, List<DescriptableDiffRow<FieldMappingView>>> =
    DescriptableDiffBuilder<FieldMappingView>(reverseOrder).apply(block).apply(DescriptableDiffBuilder<FieldMappingView>::addRowEntries).rows

/**
 * Builds a simple method diff.
 *
 * @param reverseOrder whether a reverse order [Comparator] should be used on [DiffBuilder.rows]
 * @param block the builder action
 * @return the differences, grouped by version for convenience
 */
inline fun buildMethodDiff(reverseOrder: Boolean = true, block: DescriptableDiffBuilder<MethodMappingView>.() -> Unit): Map<Version, List<DescriptableDiffRow<MethodMappingView>>> =
    DescriptableDiffBuilder<MethodMappingView>(reverseOrder).apply(block).apply(DescriptableDiffBuilder<MethodMappingView>::addRowEntries).rows
