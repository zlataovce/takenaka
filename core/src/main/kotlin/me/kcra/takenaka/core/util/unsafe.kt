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
