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

import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.VersionedMappingMap
import me.kcra.takenaka.generator.common.AbstractGenerator

/**
 * A generator that generates documentation in an HTML format.
 *
 * @param workspace the workspace in which this generator can move around
 * @author Matouš Kučera
 */
class WebGenerator(workspace: Workspace) : AbstractGenerator(workspace) {
    /**
     * Launches the generator.
     *
     * @param mappings the mappings
     */
    override fun generate(mappings: VersionedMappingMap) {
        TODO("Not yet implemented")
    }
}
