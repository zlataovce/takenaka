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
import me.kcra.takenaka.generator.web.StyleProvider

/**
 * Appends a namespace badge component.
 *
 * @param content the namespace name
 * @param color the badge color in a CSS compatible format
 */
fun FlowContent.badgeComponent(content: String, color: String, styleProvider: StyleProvider? = null) {
    if (styleProvider != null) {
        val lowercase = content.lowercase()

        p(classes = "badge ${styleProvider("badge-$lowercase", "background-color:$color;")}")
        styleProvider("badge-$lowercase::before", "content:\"$content\";")
    } else {
        p(classes = "badge") {
            style = "background-color:$color"
            +content
        }
    }
}

/**
 * Appends a namespace badge column (td) component.
 *
 * @param content the namespace name
 * @param color the badge color in a CSS compatible format
 */
fun TR.badgeColumnComponent(content: String, color: String, styleProvider: StyleProvider? = null) {
    if (styleProvider != null) {
        val lowercase = content.lowercase()

        td(classes = "badge ${styleProvider("badge-$lowercase", "background-color:$color;")}")
        styleProvider("badge-$lowercase::before", "content:\"$content\";")
    } else {
        td(classes = "badge") {
            style = "background-color:$color"
            +content
        }
    }
}
