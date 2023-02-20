package me.kcra.takenaka.generator.web.pages

import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.generator.web.ClassType
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.footerPlaceholderComponent
import me.kcra.takenaka.generator.web.components.headComponent
import me.kcra.takenaka.generator.web.components.navPlaceholderComponent
import me.kcra.takenaka.generator.web.components.spacerBottomComponent
import me.kcra.takenaka.generator.web.fromInternalName
import org.w3c.dom.Document

/**
 * Generates a package overview page.
 *
 * @param workspace the workspace
 * @param packageName the package
 * @param classes the friendly class names
 * @return the generated document
 */
fun GenerationContext.packagePage(workspace: VersionedWorkspace, packageName: String, classes: Map<String, ClassType>): Document = createHTMLDocument().html {
    val packageName0 = packageName.fromInternalName()

    headComponent(packageName0)
    body {
        navPlaceholderComponent()
        main {
            h1 {
                +packageName0
            }
            ClassType.values().forEach { type ->
                if (classes.none { (_, v) -> v == type }) return@forEach

                spacerBottomComponent()
                table(classes = "member-table row-borders") {
                    thead {
                        tr {
                            th {
                                +type.toString()
                            }
                        }
                    }
                    tbody {
                        classes.forEach { (klass, klassType) ->
                            if (klassType == type) {
                                tr {
                                    td {
                                        a(href = "/${workspace.version.id}/$packageName/$klass.html") {
                                            +klass
                                        }
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
