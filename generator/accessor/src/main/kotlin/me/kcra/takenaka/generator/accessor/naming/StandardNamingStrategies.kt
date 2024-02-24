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

package me.kcra.takenaka.generator.accessor.naming

/**
 * Standard naming strategies. All these strategies are [MemberPrefixedNamingStrategy],
 * and they suffix class names with `Mapping` and `Accessor` suffixes.
 */
enum class StandardNamingStrategies: MemberPrefixedNamingStrategy {
    /**
     * Uses simple names to name accessors and mapping classes.
     */
    SIMPLE {
        override fun klass(className: String, classType: GeneratedClassType): String {
            val index = className.lastIndexOf('.')
            return if (index != -1) {
                className.substring(index + 1)
            } else {
                className
            } + classType.asSuffix()
        }
    },

    /**
     * Uses fully qualified names to name accessors and mapping classes.
     */
    FULLY_QUALIFIED {
        override fun klass(className: String, classType: GeneratedClassType): String =
            className + classType.asSuffix()
    },

    /**
     * Uses fully qualified names without the `net.minecraft.` part to name accessors and mapping classes.
     */
    QUALIFIED_WITHOUT_NET_MINECRAFT {
        override fun klass(className: String, classType: GeneratedClassType): String =
            className.substringAfter("net.minecraft.") + classType.asSuffix()
    };

    protected fun GeneratedClassType.asSuffix() = when(this) {
        GeneratedClassType.MAPPING -> "Mapping"
        GeneratedClassType.ACCESSOR -> "Accessor"
        GeneratedClassType.EXTRA -> ""
    }
}