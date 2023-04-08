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
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.ancestry.fieldAncestryTreeOf
import me.kcra.takenaka.core.mapping.ancestry.methodAncestryTreeOf
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.modifiers
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTreeView.MethodMappingView
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import net.fabricmc.mappingio.tree.MappingTreeView.FieldMappingView
import org.w3c.dom.Document
import java.util.*

/**
 * Generates a class history page.
 *
 * @param node the ancestry node
 * @return the generated document
 */
fun GenerationContext.historyPage(node: AncestryTree.Node<ClassMappingView>): Document = createHTMLDocument().html {
    val lastFriendlyMapping = getFriendlyDstName(node.last.value).fromInternalName()

    headComponent("history - $lastFriendlyMapping")
    body {
        navPlaceholderComponent()
        main {
            h1 {
                +"History - $lastFriendlyMapping"
            }
            spacerBottomComponent()

            val classNameRows = buildDiff {
                node.forEach { (version, klass) ->
                    flushUntouchedEntries(version)
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
                    flushUntouchedEntries(version)
                    fieldTree.forEach nodeForEach@ { fieldNode ->
                        val field = fieldNode[version]
                        if (field == null) {
                            append(version, fieldNode, null)
                            return@nodeForEach
                        }

                        append(
                            version,
                            fieldNode,
                            HistoricalDescriptableDetail(
                                formatFieldDescriptor(field, version, ElementRemapper(field.tree, ::getFriendlyDstName)),
                                field.tree.allNamespaceIds
                                    .mapNotNull { id ->
                                        id to (getNamespaceFriendlyName(field.tree.getNamespaceName(id)) ?: return@mapNotNull null)
                                    }
                                    .sortedBy { generator.namespaceFriendlinessIndex.indexOf(it.second) }
                                    .mapNotNull { (id, ns) ->
                                        field.getName(id)?.let { name ->
                                            buildString {
                                                appendHTML {
                                                    textBadgeComponent(ns, getFriendlyNamespaceBadgeColor(ns), styleConsumer)
                                                }
                                                append(name)
                                            }
                                        }
                                    }
                                    .joinToString()
                            )
                        )
                    }
                }
            }

            val methodTree = methodAncestryTreeOf(node)
            val methodRows = buildMethodDiff {
                node.keys.forEach { version ->
                    flushUntouchedEntries(version)
                    methodTree.forEach nodeForEach@ { methodNode ->
                        val method = methodNode[version]
                        if (method?.srcName == "<init>") return@nodeForEach
                        if (method == null) {
                            append(version, methodNode, null)
                            return@nodeForEach
                        }

                        val methodDeclaration = formatMethodDescriptor(
                            method,
                            method.modifiers,
                            version,
                            ElementRemapper(method.tree, ::getFriendlyDstName),
                            generateNamedParameters = false
                        )

                        append(
                            version,
                            methodNode,
                            HistoricalDescriptableDetail(
                                buildString {
                                    append(methodDeclaration.args)
                                    methodDeclaration.exceptions
                                        ?.let { append(" throws $it") }
                                },
                                method.tree.allNamespaceIds
                                    .mapNotNull { id ->
                                        id to (getNamespaceFriendlyName(method.tree.getNamespaceName(id)) ?: return@mapNotNull null)
                                    }
                                    .sortedBy { generator.namespaceFriendlinessIndex.indexOf(it.second) }
                                    .mapNotNull { (id, ns) ->
                                        method.getName(id)?.let { name ->
                                            buildString {
                                                appendHTML {
                                                    textBadgeComponent(ns, getFriendlyNamespaceBadgeColor(ns), styleConsumer)
                                                }
                                                append(name)
                                            }
                                        }
                                    }
                                    .joinToString()
                            )
                        )
                    }
                }
            }

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
                spacerYComponent()
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
     * A temporary store of previously appended keys, used for differentiation.
     *
     * This is flushed every time a version's mappings are appended.
     */
    private val keys = mutableSetOf<K>()

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

        keys += key
        if (value == null) {
            values.remove(key)
        } else {
            values[key] = value
        }
    }

    /**
     * Makes all appended versions have an entry in [rows], empty or not.
     */
    fun addRowEntries() {
        versions.forEach { rows.getOrPut(it, ::mutableListOf) }
    }

    /**
     * Appends `null` for all keys that weren't touched since the last flush.
     *
     * @param version the version that should have the differences appended
     */
    fun flushUntouchedEntries(version: Version) {
        values.filterKeys { it !in keys }.forEach { (k, _) -> append(version, k, null) }
        keys.clear()
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

typealias FieldDiffBuilder = DiffBuilder<AncestryTree.Node<FieldMappingView>, HistoricalDescriptableDetail>
typealias FieldDiffRow = DiffRow<AncestryTree.Node<FieldMappingView>, HistoricalDescriptableDetail>

/**
 * Builds a simple field diff.
 *
 * @param reverseOrder whether a reverse order [Comparator] should be used on [DiffBuilder.rows]
 * @param block the builder action
 * @return the differences, grouped by version for convenience
 */
inline fun buildFieldDiff(reverseOrder: Boolean = true, block: FieldDiffBuilder.() -> Unit): Map<Version, List<FieldDiffRow>> =
    FieldDiffBuilder(reverseOrder).apply(block).apply(FieldDiffBuilder::addRowEntries).rows

typealias MethodDiffBuilder = DiffBuilder<AncestryTree.Node<MethodMappingView>, HistoricalDescriptableDetail>
typealias MethodDiffRow = DiffRow<AncestryTree.Node<MethodMappingView>, HistoricalDescriptableDetail>

/**
 * Builds a simple method diff.
 *
 * @param reverseOrder whether a reverse order [Comparator] should be used on [DiffBuilder.rows]
 * @param block the builder action
 * @return the differences, grouped by version for convenience
 */
inline fun buildMethodDiff(reverseOrder: Boolean = true, block: MethodDiffBuilder.() -> Unit): Map<Version, List<MethodDiffRow>> =
    MethodDiffBuilder(reverseOrder).apply(block).apply(MethodDiffBuilder::addRowEntries).rows
