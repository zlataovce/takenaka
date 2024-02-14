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

import kotlinx.html.*

/**
 * Appends default resources (scripts, stylesheets, meta, ...) to a head element.
 *
 * @param rootPath the path to the website root directory
 */
fun HEAD.defaultResourcesComponent(rootPath: String = "/") {
    meta(name = "viewport", content = "width=device-width, initial-scale=1")
    link(href = "${rootPath}assets/main.css", rel = "stylesheet")
    script(src = "${rootPath}assets/main.js") {}
}

/**
 * Appends a script element declaring a version root path global variable to a head element.
 *
 * @param rootPath the path to the version root directory
 */
fun HEAD.versionRootComponent(rootPath: String = "./") {
    script {
        unsafe {
            +"""window.root = "$rootPath";"""
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
