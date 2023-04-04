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

package me.kcra.takenaka.core.mapping.resolve

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionedWorkspace
import java.nio.file.Path

/**
 * A mapping file resolver.
 *
 * @author Matouš Kučera
 */
interface MappingResolver : OutputContainer<Path?> {
    /**
     * The mapping workspace.
     */
    val workspace: VersionedWorkspace

    /**
     * The mapping file output of this resolver.
     */
    val mappingOutput: Output<out Path?>
}

/**
 * A shorthand for getting the workspace version.
 */
inline val MappingResolver.version: Version
    get() = workspace.version

/**
 * A base for a mapping resolver.
 *
 * Subclasses should override the [outputs] property with their own values.
 */
abstract class AbstractMappingResolver : AbstractOutputContainer<Path?>(), MappingResolver
