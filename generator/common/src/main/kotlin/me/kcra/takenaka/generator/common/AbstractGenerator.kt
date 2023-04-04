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

package me.kcra.takenaka.generator.common

import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.MappingsMap

/**
 * An abstract base for a generator.
 *
 * A generator transforms mappings into another form, such as accessors or documentation.
 *
 * @property workspace the workspace in which this generator can move around
 * @author Matouš Kučera
 */
abstract class AbstractGenerator(val workspace: Workspace) {
    /**
     * Launches the generator with a pre-determined set of mappings.
     */
    abstract suspend fun generate(mappings: MappingsMap)
}
