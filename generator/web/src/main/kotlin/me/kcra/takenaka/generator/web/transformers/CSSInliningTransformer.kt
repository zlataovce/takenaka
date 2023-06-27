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

package me.kcra.takenaka.generator.web.transformers

import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * A transformer that inlines remote CSS `@import` declarations.
 *
 * @property exclude domains which should not have imports inlined
 * @author Matouš Kučera
 */
class CSSInliningTransformer(val exclude: List<String>) : Transformer {
    /**
     * A cache of URLs and their responses' text content.
     */
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * @param exclude domains which should not have imports inlined
     */
    constructor(vararg exclude: String) : this(exclude.toList())

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
                if (url.host !in exclude) {
                    return@replace cache.computeIfAbsent(urlString) { url.readText() }
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
