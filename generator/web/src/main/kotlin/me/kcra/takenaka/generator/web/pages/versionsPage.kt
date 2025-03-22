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

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.*
import me.kcra.takenaka.generator.web.util.*

/**
 * Generates a version list page.
 *
 * @param welcomeMessage a welcome message that is displayed at the top of this page, null if it shouldn't be added
 * @param versions the versions with their supported mappings
 * @return the generated document
 */
fun GenerationContext.versionsPage(welcomeMessage: String?, versions: Map<Version, List<String>>): String = buildHTML {
    html(lang = "en") {
        head {
            defaultResourcesComponent(rootPath = "")
            title {
                append("mappings")
            }
        }
        body {
            navPlaceholderComponent()
            main {
                if (welcomeMessage != null) {
                    div(classes = "welcome-message") {
                        append(welcomeMessage)
                        spacerBottomComponent()
                    }
                }

                table(classes = "styled-table styled-mobile-table") {
                    thead {
                        tr {
                            th {
                                append("Version")
                            }
                            th {
                                append("Mappings")
                            }
                        }
                    }
                    tbody {
                        versions.forEach { (version, mappings) ->
                            tr {
                                td {
                                    a(href = "${version.id}/index.html") {
                                        append(version.id)
                                    }
                                }
                                td(classes = "mapping-badges") {
                                    mappings.forEach { ns ->
                                        generator.config.namespaces[ns]
                                            ?.let { badgeComponent(it.friendlyName, it.color, styleProvider) }
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
}