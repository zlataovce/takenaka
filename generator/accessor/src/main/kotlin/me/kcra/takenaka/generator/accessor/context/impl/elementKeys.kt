/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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

package me.kcra.takenaka.generator.accessor.context.impl

/**
 * A [Map] key associated with a mapping namespace, useful for grouping a mapping by multiple versions.
 */
interface NamespacedKey {
    /**
     * The mapping namespace.
     */
    val namespace: String
}

/**
 * A class mapping key.
 *
 * @property namespace the mapping namespace
 * @property name the mapped class name
 */
data class ClassKey(override val namespace: String, val name: String) : NamespacedKey

/**
 * A field mapping key.
 *
 * @property namespace the mapping namespace
 * @property name the mapped field name
 */
data class FieldKey(override val namespace: String, val name: String) : NamespacedKey

/**
 * A constructor mapping key.
 *
 * @property namespace the mapping namespace
 * @property descriptor the mapped constructor descriptor
 */
data class ConstructorKey(override val namespace: String, val descriptor: String) : NamespacedKey

/**
 * A method mapping key.
 *
 * *The method return type is not used when checking equality, mimicking reflective lookups.*
 *
 * @property namespace the mapping namespace
 * @property name the mapped method name
 * @property descriptor the mapped method descriptor
 */
data class MethodKey(override val namespace: String, val name: String, val descriptor: String) : NamespacedKey {
    /**
     * [descriptor], but with the return type removed.
     */
    private val partialDescriptor: String

    init {
        val parenthesisIndex = descriptor.lastIndexOf(')')
        this.partialDescriptor = if (parenthesisIndex != -1) descriptor.substring(0, parenthesisIndex + 1) else descriptor
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MethodKey

        if (namespace != other.namespace) return false
        if (name != other.name) return false
        if (partialDescriptor != other.partialDescriptor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + partialDescriptor.hashCode()
        return result
    }
}
