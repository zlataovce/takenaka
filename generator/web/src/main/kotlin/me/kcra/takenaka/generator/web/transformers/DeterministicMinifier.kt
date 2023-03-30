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

import kotlin.concurrent.withLock
import kotlin.math.abs

/**
 * A transformer that minifies web resources in a deterministic way.
 *
 * @author Matouš Kučera
 */
class DeterministicMinifier : Minifier() {
    override fun nextMinifiedClass(): String {
        throw UnsupportedOperationException("nextMinifiedClass is not supported")
    }

    /**
     * Minifies raw HTML markup.
     *
     * @param content the raw HTML markup
     * @return the transformed markup
     */
    override fun transformHtml(content: String): String = lock.withLock {
        content.replace(CLASS_ATTR_REGEX) { m ->
            """class="${m.groups[1]?.value?.split(' ')?.joinToString(" ") { klass ->
                // minify class names based on their hash code, shortened to Short.MAX_VALUE
                classes.computeIfAbsent(klass) { k -> minifiedClass(abs(k.hashCode().toShort().toInt())) }
            } ?: ""}""""
        }
    }
}
