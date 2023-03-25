package me.kcra.takenaka.generator.web.pages

import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import me.kcra.takenaka.core.mapping.allNamespaceIds
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import org.w3c.dom.Document

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

            val nameMap = mutableMapOf<String, String?>()

            node.entries.forEach f1@ { (version, klass) ->
                h3 {
                    +version.id
                }
                klass.tree.allNamespaceIds.forEach { id ->
                    val ns = klass.tree.getNamespaceName(id)
                    val nsFriendlyName = getNamespaceFriendlyName(ns) ?: return@forEach

                    val name = klass.getName(id)?.fromInternalName()
                    val oldName = nameMap[ns]
                    if (oldName != name) {
                        if (oldName != null) {
                            p {
                                style = "color: var(--text);"

                                +"- $nsFriendlyName: $oldName"
                            }
                        }
                        if (name != null) {
                            p {
                                style = "color: var(--text);"

                                +"+ $nsFriendlyName: $name"
                            }
                        }
                    }

                    nameMap[ns] = name
                }
                spacerYComponent()
            }
        }
        footerPlaceholderComponent()
    }
}
