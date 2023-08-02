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

package me.kcra.takenaka.generator.web.components

import kotlinx.html.*

/**
 * Appends a navbar component.
 */
fun FlowContent.navComponent() {
    nav {
        div(classes = "nav-items") {
            a(classes = "nav-brand", href = "/index.html") {
                +"mappings"
            }
            div(classes = "nav-links") {
                a(classes = "nav-link", href = "#") {
                    id = "overview-link"

                    +"Overview"
                }
                a(classes = "nav-link", href = "#") {
                    id = "licenses-link"

                    +"Licenses"
                }
            }
        }
        div(classes = "utils-box") {
            div(classes = "search-input-box") {
                input(type = InputType.text, classes = "search-input") {
                    id = "search-input"

                    placeholder = "Type a package name..."
                }
                p(classes = "icon-button") {
                    onClick = "toggleTheme()"
                    unsafe {
                        +"""<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>"""
                    }
                }
            }
            div(classes = "search-box") {
                id = "search-box"
                style = "display:none"

                div(classes = "option-box-toggle") {
                    onClick = "toggleOptions()"

                    p {
                        +"Namespaces"
                    }
                    p {
                        unsafe {
                            +"""<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>"""
                        }
                    }
                }
                div(classes = "option-box") {
                    id = "option-box"
                    style = "display:none"
                }
                div {
                    id = "search-results-box"
                }
            }
        }
    }
}

/**
 * Appends a navbar component placeholder that is replaced with a real navbar dynamically.
 */
fun FlowContent.navPlaceholderComponent() {
    nav {}
}
