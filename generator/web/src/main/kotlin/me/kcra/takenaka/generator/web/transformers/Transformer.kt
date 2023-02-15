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

/**
 * A HTML/CSS post-processor.
 *
 * @author Matouš Kučera
 */
interface Transformer {
    /**
     * Transforms raw HTML markup.
     *
     * @param content the raw HTML markup
     * @return the transformed markup
     */
    fun transformHtml(content: String): String {
        return content
    }

    /**
     * Transforms raw CSS styles.
     *
     * @param content the raw CSS styles
     * @return the transformed stylesheet
     */
    fun transformCss(content: String): String {
        return content
    }

    /**
     * Transforms raw JavaScript code.
     *
     * @param content the JS code
     * @return the transformed code
     */
    fun transformJs(content: String): String {
        return content
    }
}
