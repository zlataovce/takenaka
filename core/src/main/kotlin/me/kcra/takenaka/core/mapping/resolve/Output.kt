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

package me.kcra.takenaka.core.mapping.resolve

import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * A generic output.
 *
 * @param T output type
 * @author Matouš Kučera
 */
interface Output<T> {
    /**
     * The output value.
     */
    val value: T

    /**
     * Is the output up-to-date (e.g. if it's a File, does the file still exist?)?
     */
    val isUpToDate: Boolean

    /**
     * Resolves this output.
     *
     * Implementations should set [value] to the resolved value
     * and resolve the value even if it's already up-to-date.
     *
     * @return the value
     */
    fun resolve(): T

    /**
     * Gets the output value by delegation.
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

/**
 * An up-to-date-ness checking function.
 */
typealias UpToDateWhen<T> = (T) -> Boolean

internal object UninitializedValue

/**
 * An output builder.
 *
 * @param T the output type
 * @author Matouš Kučera
 */
class OutputBuilder<T> {
    /**
     * The initializer function.
     */
    private var initializer: () -> T by Delegates.notNull()

    /**
     * The up-to-date func, defaults to a null check.
     */
    private var upToDateWhenFunc: UpToDateWhen<T> = { it != null }

    /**
     * Sets the resolver function.
     */
    fun resolver(block: () -> T) {
        initializer = block
    }

    /**
     * Sets the up-to-date checking function.
     */
    fun upToDateWhen(block: UpToDateWhen<T>) {
        upToDateWhenFunc = block
    }

    /**
     * Creates a lazily-fetched output from this builder.
     *
     * This implementation will resolve a value when:
     *  - it hasn't been resolved yet.
     *  - it's no longer up-to-date.
     *
     * @return the output
     */
    fun toLazyOutput(): Output<T> {
        return object : Output<T> {
            @Volatile
            private var _value: Any? = UninitializedValue

            override val value: T
                get() {
                    return synchronized(this) {
                        val v = _value
                        if (_value !== UninitializedValue && isUpToDate) {
                            @Suppress("UNCHECKED_CAST")
                            v as T
                        } else {
                            resolve()
                        }
                    }
                }

            override val isUpToDate: Boolean
                get() {
                    synchronized(this) {
                        val v = _value

                        @Suppress("UNCHECKED_CAST")
                        return v !== UninitializedValue && upToDateWhenFunc(v as T)
                    }
                }

            override fun resolve(): T {
                val typedValue = initializer()
                synchronized(this) {
                    _value = typedValue
                }
                return typedValue
            }
        }
    }
}

/**
 * Creates a lazily-fetched output from a builder.
 *
 * @param T the output type
 * @param block the builder action
 * @return the output
 */
inline fun <T> lazyOutput(block: OutputBuilder<T>.() -> Unit): Output<T> = OutputBuilder<T>().apply(block).toLazyOutput()
