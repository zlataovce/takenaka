package me.kcra.takenaka.accessor.util.kotlin

import me.kcra.takenaka.accessor.util.LazySupplier
import kotlin.reflect.KProperty

/**
 * Creates a new [LazySupplier].
 *
 * @param block the value supplier
 * @return the lazy supplier
 */
fun <T> lazy(block: () -> T): LazySupplier<T> = LazySupplier.of(block)

/**
 * Gets the lazily-fetched value by delegation.
 */
operator fun <T> LazySupplier<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
