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

package me.kcra.takenaka.generator.accessor

import me.kcra.takenaka.generator.accessor.model.ClassAccessor

/**
 * Configuration for [AccessorGenerator].
 *
 * @property accessors the class accessor models
 * @property flavor the flavor of the generated code
 * @author Matouš Kučera
 */
data class AccessorConfiguration(
    val accessors: List<ClassAccessor>,
    val flavor: AccessorFlavor = AccessorFlavor.JAVA
)
