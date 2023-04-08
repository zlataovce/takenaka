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

import kotlinx.html.FlowContent
import kotlinx.html.body
import kotlinx.html.consumers.filter
import kotlinx.html.html
import kotlinx.html.stream.appendHTML

/**
 * Appends raw HTML to a string builder.
 *
 * **This function should be used with care, most strings should not contain raw HTML.**
 *
 * @param block the HTML builder
 */
fun StringBuilder.appendHTML(block: FlowContent.() -> Unit) {
    appendHTML(false)
        .filter { if (it.tagName == "html" || it.tagName == "body") SKIP else PASS }
        .html {
            body {
                block()
            }
        }
}