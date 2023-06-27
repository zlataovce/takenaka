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

package me.kcra.takenaka.generator.web

import java.util.concurrent.ConcurrentHashMap

/**
 * A provider for generated CSS class names.
 *
 * @author Matouš Kučera
 */
interface StyleProvider {
    /**
     * Provides a CSS class name from a key and the style content.
     *
     * @param key the style key
     * @param style the CSS style
     * @return the generated CSS class name
     */
    fun apply(key: String, style: String): String

    /**
     * Creates a CSS stylesheet to be included in generated output.
     *
     * @return the stylesheet
     */
    fun asStyleSheet(): String
}

/**
 * Base [StyleProvider] implementation.
 *
 * @property styles currently provided styles, keyed (**this map should be thread-safe**)
 */
class StyleProviderImpl(val styles: MutableMap<String, String> = ConcurrentHashMap()) : StyleProvider {
    /**
     * Provides a CSS class name from a key and the style content.
     *
     * @param key the style key
     * @param style the CSS style
     * @return the generated CSS class name
     */
    override fun apply(key: String, style: String): String {
        styles.putIfAbsent(key, style)
        return key
    }

    /**
     * Generates a stylesheet from gathered classes.
     *
     * @return the stylesheet content
     */
    override fun asStyleSheet(): String {
        return styles.entries.joinToString("") { (k, s) ->
            """
                .$k {
                    $s
                }
            """.trimIndent()
        }
    }
}
