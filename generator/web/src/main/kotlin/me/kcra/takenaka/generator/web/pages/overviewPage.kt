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
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.footerPlaceholderComponent
import me.kcra.takenaka.generator.web.components.headComponent
import me.kcra.takenaka.generator.web.components.navPlaceholderComponent
import me.kcra.takenaka.generator.web.components.spacerBottomComponent
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
