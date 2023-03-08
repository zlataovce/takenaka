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

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.reflect.KClass

/**
 * The field of [java.util.LinkedHashMap] holding the first (eldest) entry.
 */
private val HEAD_FIELD = LinkedHashMap::class.getFieldHandle("head")

/**
 * The field of [java.util.LinkedHashMap] holding the last (youngest) entry.
 */
private val TAIL_FIELD = LinkedHashMap::class.getFieldHandle("tail")

/**
 * Gets a field handle in a class.
 *
 * @param name the field name
 * @return the field handle, null if an error occurred
 */
private fun KClass<*>.getFieldHandle(name: String): MethodHandle? =
    try { MethodHandles.lookup().unreflectGetter(java.getDeclaredField(name).apply { isAccessible = true }) } catch (_: Exception) { null }

/**
 * Returns the first entry, utilizing implementation details of the underlying map to improve performance, if possible.
 *
 * @return the entry
 * @throws NoSuchElementException if the collection is empty
 */
fun <K, V> Map<K, V>.firstEntryUnsafe(): Map.Entry<K, V> {
    if (HEAD_FIELD != null && this is LinkedHashMap) {
        @Suppress("UNCHECKED_CAST")
        return (HEAD_FIELD.invoke(this) as Map.Entry<K, V>?)
            ?: throw NoSuchElementException("Collection is empty.")
    }

    return this.entries.first()
}

/**
 * Returns the last entry, utilizing implementation details of the underlying map to improve performance, if possible.
 *
 * @return the entry
 * @throws NoSuchElementException if the collection is empty
 */
fun <K, V> Map<K, V>.lastEntryUnsafe(): Map.Entry<K, V> {
    if (TAIL_FIELD != null && this is LinkedHashMap) {
        @Suppress("UNCHECKED_CAST")
        return (TAIL_FIELD.invoke(this) as Map.Entry<K, V>?)
            ?: throw NoSuchElementException("Collection is empty.")
    }

    return this.entries.last()
}
