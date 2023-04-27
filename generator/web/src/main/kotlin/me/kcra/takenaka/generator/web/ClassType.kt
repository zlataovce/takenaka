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

package me.kcra.takenaka.generator.web

import org.objectweb.asm.Opcodes
import java.util.*

/**
 * A class type.
 *
 * @param plural the plural version of the type
 * @author Matouš Kučera
 */
enum class ClassType(val plural: String) {
    CLASS("classes"),
    INTERFACE("interfaces"),
    ENUM("enums"),
    ANNOTATION("annotations");

    override fun toString(): String = name.lowercase()
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/**
 * Makes a class type from a modifier.
 *
 * @param mod the modifier
 * @return the type
 */
fun classTypeOf(mod: Int): ClassType = when {
    (mod and Opcodes.ACC_ANNOTATION) != 0 -> ClassType.ANNOTATION // annotations are interfaces, so this must be before ACC_INTERFACE
    (mod and Opcodes.ACC_INTERFACE) != 0 -> ClassType.INTERFACE
    (mod and Opcodes.ACC_ENUM) != 0 -> ClassType.ENUM
    else -> ClassType.CLASS
}
