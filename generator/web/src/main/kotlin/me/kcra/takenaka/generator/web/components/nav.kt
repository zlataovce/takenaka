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

package me.kcra.takenaka.generator.web.components

import me.kcra.takenaka.generator.web.util.*

/**
 * Appends a navbar component.
 */
fun HTMLBuilder.navComponent() {
    nav {
        div(classes = "nav-items") {
            a(classes = "nav-brand", href = "/index.html") {
                append("mappings")
            }
            div(classes = "nav-links") {
                a(id = "overview-link", classes = "nav-link", href = "#") {
                    append("Overview")
                }
                a(id = "licenses-link", classes = "nav-link", href = "#") {
                    append("Licenses")
                }
            }
        }
        div(classes = "utils-box") {
            div(classes = "search-input-box") {
                input(id = "search-input", type = "text", classes = "search-input", spellCheck = "false", placeholder = "Type a package name...")
                p(classes = "icon-button", onClick = "toggleTheme()") {
                    append("""<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>""")
                }
            }
            div(id = "search-box", classes = "search-box", style = "display:none") {
                div(classes = "option-box-toggle", onClick = "toggleOptions()") {
                    p {
                        append("Namespaces")
                    }
                    p {
                        append("""<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>""")
                    }
                }
                div(id = "option-box", classes = "option-box", style = "display:none")
                div(id = "search-results-box")
            }
        }
    }
}

/**
 * Appends a navbar component placeholder that is replaced with a real navbar dynamically.
 */
fun HTMLBuilder.navPlaceholderComponent() {
    nav {}
}
