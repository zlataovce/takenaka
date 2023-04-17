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
            table(classes = "member-table") {
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
                            td(classes = "mapping-badges") {
                                mappings.forEach { ns ->
                                    generator.config.namespaces[ns]
                                        ?.let { badgeComponent(it.friendlyName, it.color, styleConsumer) }
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