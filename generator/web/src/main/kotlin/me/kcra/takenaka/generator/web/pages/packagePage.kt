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
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.web.ClassType
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.footerPlaceholderComponent
import me.kcra.takenaka.generator.web.components.headComponent
import me.kcra.takenaka.generator.web.components.navPlaceholderComponent
import me.kcra.takenaka.generator.web.components.spacerBottomComponent
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

    headComponent(packageName0, workspace.version.id)
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
