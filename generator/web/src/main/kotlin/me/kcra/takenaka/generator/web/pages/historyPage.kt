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
import me.kcra.takenaka.core.mapping.allNamespaceIds
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.StyleConsumer
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
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
                node.entries.forEach { (version, klass) ->
                    klass.tree.allNamespaceIds.forEach { id ->
                        val ns = klass.tree.getNamespaceName(id)

                        val nsFriendlyName = getNamespaceFriendlyName(ns)
                        if (nsFriendlyName != null) {
                            append(version, nsFriendlyName, klass.getName(id)?.fromInternalName())
                        }
                    }
                }
            }

            classNameRows.forEach { (version, rows) ->
                h3 {
                    +version.id
                }
                rows.forEach { row ->
                    p(classes = "diff-${row.type}") {
                        textBadgeComponent(row.key, getFriendlyNamespaceBadgeColor(row.key), styleConsumer)
                        unsafe {
                            +row.value
                        }
                    }
                }
                spacerYComponent()
            }
        }
        footerPlaceholderComponent()
    }
}

/**
 * Appends a namespace text badge component.
 *
 * @param content the namespace name
 * @param color the badge color in a CSS compatible format
 * @param styleConsumer the style provider, used for generating stylesheets
 */
fun FlowContent.textBadgeComponent(content: String, color: String, styleConsumer: StyleConsumer) {
    val lowercase = content.lowercase()

    span(classes = "badge-text ${styleConsumer("badge-text-$lowercase", "font-family:var(--font-monospace);")}")
    styleConsumer("badge-text-$lowercase::before", "color:$color;content:\"$content\";")
    styleConsumer("badge-text-$lowercase::after", "content:\": \";")
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
    private val values = mutableMapOf<K, V?>()

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
     *    rows with both types are appended (former value row is a [DiffType.DELETION], [value] row is an [DiffType.ADDITION]).
     *
     * @param version the version of the current [value]
     * @param key the cross-version key, most likely a namespace
     * @param value the version-dependent value, most likely a mapping
     */
    fun append(version: Version, key: K, value: V?) {
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

        values[key] = value
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

/**
 * Builds a simple string-string diff.
 *
 * @param reverseOrder whether a reverse order [Comparator] should be used on [DiffBuilder.rows]
 * @param block the builder action
 * @return the differences, grouped by version for convenience
 */
inline fun buildDiff(reverseOrder: Boolean = true, block: DiffBuilder<String, String>.() -> Unit): Map<Version, List<DiffRow<String, String>>> =
    DiffBuilder<String, String>(reverseOrder).apply(block).rows
