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

package me.kcra.takenaka.generator.web.transformers

import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * A transformer that inlines remote CSS `@import` declarations.
 *
 * *This transformer does not do recursive inlining - inlined stylesheets do not have their imports inlined*.
 *
 * @property include domains which should have imports inlined
 * @author Matouš Kučera
 */
class CSSInliningTransformer(val include: List<String>) : Transformer {
    /**
     * A cache of URLs and their responses' text content.
     */
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Constructs a new [CSSInliningTransformer].
     *
     * @param include domains which should have imports inlined
     */
    constructor(vararg include: String) : this(include.toList())

    /**
     * Inlines remote `@import` declarations in a raw CSS stylesheet.
     *
     * @param content the raw CSS styles
     * @return the transformed stylesheet
     */
    override fun transformCss(content: String): String {
        return content.replace(IMPORT_REGEX) { m ->
            val urlString = m.groups[1]!!.value
            if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                val url = URL(urlString)
                if (url.host in include) {
                    return@replace cache.getOrPut(urlString) { url.readText() }
                }
            }

            return@replace m.value
        }
    }

    companion object {
        /**
         * The `@import` statement matching regular expression.
         */
        val IMPORT_REGEX = "@import url\\(['\"]([^'\"]+?)['\"]\\);".toRegex()
    }
}
