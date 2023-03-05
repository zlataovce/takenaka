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

package me.kcra.takenaka.core.util

/**
 * The field of [java.util.LinkedHashMap] holding the last entry.
 */
private val TAIL_FIELD = LinkedHashMap::class.java.getDeclaredField("tail").apply { isAccessible = true }

/**
 * Returns the last entry, utilizing implementation details of the underlying map to improve performance, if possible.
 *
 * @throws NoSuchElementException if the collection is empty
 */
fun <K, V> Map<K, V>.lastEntryUnsafe(): Map.Entry<K, V> {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is LinkedHashMap -> {
            try {
                (TAIL_FIELD.get(this) as Map.Entry<K, V>?)
                    ?: throw NoSuchElementException("Collection is empty.")
            } catch (_: Exception) {
                this.entries.last()
            }
        }
        else -> this.entries.last()
    }
}
