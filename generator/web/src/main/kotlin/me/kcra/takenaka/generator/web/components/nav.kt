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
            p(classes = "nav-brand") {
                +"placeholder"
            }
            p {
                +"Overview"
            }
        }
        div(classes = "search-box") {
            p(classes = "button-icon") {
                attributes["onClick"] = "toggleTheme()"
                unsafe {
                    +"""
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" /></svg>
                    """.trimIndent()
                }
            }
            input(type = InputType.text, classes = "search-input") {
                attributes["placeholder"] = "Type a package name..."
            }
            p(classes = "button-icon") {
                unsafe {
                    +"""
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
                    """.trimIndent()
                }
            }
        }
    }
}