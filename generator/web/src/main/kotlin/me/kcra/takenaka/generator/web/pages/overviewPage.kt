package me.kcra.takenaka.generator.web.pages

import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.footerPlaceholderComponent
import me.kcra.takenaka.generator.web.components.headComponent
import me.kcra.takenaka.generator.web.components.navPlaceholderComponent
import me.kcra.takenaka.generator.web.components.spacerBottomComponent
import me.kcra.takenaka.generator.web.fromInternalName
import org.w3c.dom.Document

/**
 * Generates an overview page.
 *
 * @param workspace the workspace
 * @param packages the packages in this version
 * @return the generated document
 */
fun GenerationContext.overviewPage(workspace: VersionedWorkspace, packages: Set<String>): Document = createHTMLDocument().html {
    headComponent(workspace.version.id)
    body {
        navPlaceholderComponent()
        main {
            h1 {
                +workspace.version.id
            }
            spacerBottomComponent()
            table(classes = "member-table row-borders") {
                thead {
                    tr {
                        th {
                            +"Package"
                        }
                    }
                }
                tbody {
                    packages.forEach { packageName ->
                        tr {
                            td {
                                a(href = "/${workspace.version.id}/$packageName/index.html") {
                                    +packageName.fromInternalName()
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
