/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.License
import me.kcra.takenaka.generator.web.components.*
import org.w3c.dom.Document

private val PROTOCOL_REGEX = "^[a-z-]+://.+$".toRegex()

/**
 * Generates a license page.
 *
 * @param workspace the workspace
 * @param licenses a map of namespace-license
 * @return the generated document
 */
fun GenerationContext.licensePage(workspace: VersionedWorkspace, licenses: Map<String, License>): Document = createHTMLDocument().html {
    lang = "en"
    head {
        versionRootComponent()
        defaultResourcesComponent(rootPath = "../")
        title(content = "licenses - ${workspace.version.id}")
    }
    body {
        navPlaceholderComponent()
        main {
            h1 {
                +"Licenses - ${workspace.version.id}"
            }
            spacerBottomComponent()

            licenses.entries.forEachIndexed { i, (ns, license) ->
                val namespace = generator.config.namespaces[ns]
                if (namespace != null) {
                    div(classes = "license-header") {
                        badgeComponent(namespace.friendlyName, namespace.color, styleProvider)

                        if (license.source.matches(PROTOCOL_REGEX)) {
                            a(href = license.source) {
                                +license.source
                            }
                        } else {
                            p {
                                +license.source
                            }
                        }
                    }

                    pre {
                        +license.content.replace("\\n", "\n")
                    }

                    if (i != (licenses.size - 1)) {
                        spacerYComponent()
                    }
                }
            }
        }
        footerPlaceholderComponent()
    }
}
