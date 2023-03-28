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

import java.io.Serializable
import kotlin.reflect.KProperty

/**
 * A lazily initialized value that can be re-initialized.
 *
 * @author Matouš Kučera
 */
interface ResettableLazy<T> : Lazy<T> {
    /**
     * Un-initializes this value.
     */
    fun reset()

    /**
     * Retrieves the value by delegation.
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

/**
 * Creates a new [ResettableLazy] that synchronizes on itself to ensure thread safety.
 *
 * @param initializer the initialization function
 * @return the lazy
 */
fun <T> resettableLazy(initializer: () -> T): ResettableLazy<T> = SynchronizedResettableLazyImpl(initializer)

internal object UninitializedValue

private class SynchronizedResettableLazyImpl<T>(private val initializer: () -> T) : ResettableLazy<T>, Serializable {
    @Volatile
    private var _value: Any? = UninitializedValue

    override val value: T
        get() {
            return synchronized(this) {
                val v = _value
                if (v !== UninitializedValue) {
                    @Suppress("UNCHECKED_CAST")
                    v as T
                } else {
                    val typedValue = initializer()
                    _value = typedValue
                    typedValue
                }
            }
        }

    override fun isInitialized(): Boolean = _value !== UninitializedValue
    override fun reset() {
        synchronized(this) {
            _value = UninitializedValue
        }
    }
    override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."
}
