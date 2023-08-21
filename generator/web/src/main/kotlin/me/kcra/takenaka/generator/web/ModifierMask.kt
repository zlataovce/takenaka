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

import java.lang.reflect.Modifier

/**
 * Enumerated modifier masks for Java language elements.
 *
 * @property value the mask value
 * @author Matouš Kučera
 */
enum class ModifierMask(val value: Int) {
    CLASS(Modifier.classModifiers()),
    INTERFACE(Modifier.interfaceModifiers()),
    CONSTRUCTOR(Modifier.constructorModifiers()),
    METHOD(Modifier.methodModifiers()),
    FIELD(Modifier.fieldModifiers()),
    PARAMETER(Modifier.parameterModifiers()),
    NONE(0)
}

/**
 * Performs a bitwise AND operation on this integer value and the value of the specified [ModifierMask].
 *
 * @param mask the modifier mask
 * @return the masked value
 */
infix fun Int.and(mask: ModifierMask): Int = this and mask.value

/**
 * Makes a modifier mask from a class type.
 *
 * @param type the class type
 * @return the modifier mask
 */
fun modifierMaskOf(type: ClassType): ModifierMask = when (type) {
    ClassType.CLASS, ClassType.ENUM -> ModifierMask.CLASS
    ClassType.INTERFACE, ClassType.ANNOTATION -> ModifierMask.INTERFACE
}
