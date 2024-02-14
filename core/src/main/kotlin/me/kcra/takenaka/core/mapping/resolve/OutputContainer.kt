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

package me.kcra.takenaka.core.mapping.resolve

/**
 * A list of outputs with the same type.
 *
 * @param T the output type
 * @author Matouš Kučera
 */
interface OutputContainer<T> : List<Output<out T>>

/**
 * A base for an output container.
 *
 * @param T the output type
 * @property outputs the list of outputs, this should be overridden by subclasses
 */
open class AbstractOutputContainer<T>(protected open val outputs: List<Output<out T>> = emptyList()) : OutputContainer<T>, List<Output<out T>> by outputs
