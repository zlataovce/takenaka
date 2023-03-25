package me.kcra.takenaka.generator.web.pages

import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.allNamespaceIds
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.web.GenerationContext
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
                            append(nsFriendlyName, klass.getName(id)?.fromInternalName(), version)
                        }
                    }
                }
            }

            classNameRows.forEach { (version, rows) ->
                h3 {
                    +version.id
                }
                rows.forEach { row ->
                    when (row.type) {
                        DiffType.DELETION -> {
                            p {
                                style = "color: var(--text);"

                                +"- ${row.key}: ${row.value}"
                            }
                        }
                        DiffType.ADDITION -> {
                            p {
                                style = "color: var(--text);"

                                +"+ ${row.key}: ${row.value}"
                            }
                        }
                    }
                }
                spacerYComponent()
            }
        }
        footerPlaceholderComponent()
    }
}

class DiffBuilder<K, V>(reverseOrder: Boolean = true) {
    private val values = mutableMapOf<K, V?>()
    val rows: SortedMap<Version, MutableList<DiffRow<K, V>>> = TreeMap(
        if (reverseOrder) Collections.reverseOrder() else null
    )

    fun append(key: K, value: V?, version: Version) {
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

data class DiffRow<K, V>(val type: DiffType, val version: Version, val key: K, val value: V)

enum class DiffType {
    DELETION,
    ADDITION
}

inline fun buildDiff(reverseOrder: Boolean = true, block: DiffBuilder<String, String>.() -> Unit): Map<Version, List<DiffRow<String, String>>> =
    DiffBuilder<String, String>(reverseOrder).apply(block).rows
