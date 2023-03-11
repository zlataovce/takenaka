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

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A transformer that minifies web resources.
 *
 * @author Matouš Kučera
 */
open class Minifier : Transformer {
    /**
     * The transformation lock.
     */
    protected val lock = ReentrantLock()

    /**
     * Amount of unique visited class names, used as an entropy for generating a class name.
     */
    protected var classIndex = 0

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
    override fun transformHtml(content: String): String = lock.withLock {
        content.replace(CLASS_ATTR_REGEX) { m ->
            """class="${m.groups[1]?.value?.split(' ')?.joinToString(" ") { classes.computeIfAbsent(it) { nextMinifiedClass() } } ?: ""}""""
        }
    }

    /**
     * Generates a unique CSS class name.
     * The class name is unique only in the context of this minifier.
     *
     * @return the class name
     */
    fun nextMinifiedClass(): String = minifiedClass(classIndex++)

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

        lock.withLock {
            classes.forEach { (original, minified) ->
                remappedContent = remappedContent.replace("\\.$original([ :])".toRegex()) { ".$minified${it.groups[1]?.value}" }
            }
        }

        return remappedContent
    }

    /**
     * Minifies raw JavaScript code.
     *
     * @param content the JS code
     * @return the transformed code
     */
    override fun transformJs(content: String): String {
        // doesn't need to be synchronized, it's not touching any CSS classes
        return content.replace(COMMENT_REGEX, "")
            .split("\r\n", "\n")
            .map(String::trim)
            .filter { !it.startsWith("//") }
            .joinToString("")
    }

    companion object {
        val CLASS_ATTR_REGEX = """class="([a-z- ]+?)"""".toRegex()
        val COMMENT_REGEX = "/\\*.*?\\*/".toRegex(RegexOption.DOT_MATCHES_ALL)
    }
}

/**
 * Generates an alphabetical string based on an integer value.
 *
 * @param i the integer
 * @return the string
 */
fun minifiedClass(i: Int): String = if (i < 0) "" else minifiedClass(i / 26 - 1) + (65 + i % 26).toChar()
