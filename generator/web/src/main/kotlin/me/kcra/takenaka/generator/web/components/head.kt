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
 * Appends default resources (scripts, stylesheets, meta, ...) to a head element.
 *
 * @param version the Minecraft version of the page's context, the corresponding class search index is added if not null
 */
fun HEAD.defaultResourcesComponent(version: String? = null) {
    meta(name = "viewport", content = "width=device-width, initial-scale=1")
    link(href = "/assets/main.css", rel = "stylesheet")
    script(src = "/assets/main.js") {}
    script(src = "/assets/components.js") {}
    if (version != null) {
        script(src = "/$version/class-index.js") {
            async = true
        }
    }
}

/**
 * Appends [OpenGraph](https://ogp.me/#metadata) and `theme-color` metadata to a head element.
 */
fun HEAD.metadataComponent(title: String? = null, description: String? = null, themeColor: String? = null) {
    if (themeColor != null) meta(name = "theme-color", content = themeColor)

    // https://ogp.me/#metadata
    if (title != null) meta(name = "og:title", content = title)
    if (description != null) meta(name = "og:description", content = description)
}
