package me.kcra.takenaka.generator.web.pages

import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.badgeComponent
import me.kcra.takenaka.generator.web.components.footerPlaceholderComponent
import me.kcra.takenaka.generator.web.components.headComponent
import me.kcra.takenaka.generator.web.components.navPlaceholderComponent
import org.w3c.dom.Document

/**
 * Generates a version list page.
 *
 * @param versions the versions with their supported mappings
 * @return the generated document
 */
fun GenerationContext.versionsPage(versions: Map<Version, List<String>>): Document = createHTMLDocument().html {
    headComponent("mappings")
    body {
        navPlaceholderComponent()
        main {
            table(classes = "member-table row-borders") {
                thead {
                    tr {
                        th {
                            +"Version"
                        }
                        th {
                            +"Mappings"
                        }
                    }
                }
                tbody {
                    versions.forEach { (version, mappings) ->
                        tr {
                            td {
                                a(href = "/${version.id}/index.html") {
                                    +version.id
                                }
                            }
                            td {
                                style = "display:flex"
                                mappings.forEach { mappingType ->
                                    val nsFriendlyName = generator.namespaceFriendlyNames[mappingType]
                                    if (nsFriendlyName != null) {
                                        badgeComponent(nsFriendlyName, generator.namespaceBadgeColors[mappingType] ?: "#94a3b8", styleSupplier)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        footerPlaceholderComponent()
    }
}