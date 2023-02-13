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

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A transformer that minifies stylesheets.
 *
 * @author Matouš Kučera
 */
class Minifier : Transformer {
    /**
     * Amount of unique visited class names, used as an entropy for generating a class name.
     */
    private var classIndex = 0

    /**
     * A mapping of original -> minified CSS class names.
     */
    val classes: MutableMap<String, String> = mutableMapOf()

    /**
     * Minifies raw HTML markup.
     *
     * @param content the raw HTML markup
     * @return the transformed markup
     */
    override fun transformHtml(content: String): String {
        return content.replace(CLASS_ATTR_REGEX) { m ->
            """class="${m.groups[1]!!.value.split(' ').joinToString(" ") {
                logger.debug { "Minifying CSS class $it in markup" } 
                classes.computeIfAbsent(it) { nextMinifiedClass() }
            }}""""
        }
    }

    /**
     * Generates a unique CSS class name.
     * The class name is unique only in the context of this minifier.
     *
     * @return the class name
     */
    fun nextMinifiedClass(): String = nextMinifiedClass(classIndex++)

    private fun nextMinifiedClass(i: Int): String =
        if (i < 0) "" else nextMinifiedClass(i / 26 - 1) + (65 + i % 26).toChar()

    /**
     * Minifies raw CSS styles.
     *
     * @param content the raw CSS styles
     * @return the transformed stylesheet
     */
    override fun transformCss(content: String): String {
        var remappedContent = content.split("\r\n", "\n")
            .joinToString("") { it.trim() }
            .replace(COMMENT_REGEX, "")
        classes.forEach { (original, minified) ->
            logger.debug { "Minifying CSS class $original to $minified in a stylesheet" }
            remappedContent = remappedContent.replace(".$original", ".$minified")
        }

        return remappedContent
    }

    companion object {
        private val CLASS_ATTR_REGEX = """class="([a-z- ]+?)"""".toRegex()
        private val COMMENT_REGEX = "/\\*.*?\\*/".toRegex()
    }
}
