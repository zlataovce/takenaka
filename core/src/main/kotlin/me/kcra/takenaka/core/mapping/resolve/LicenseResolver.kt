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

import me.kcra.takenaka.core.Workspace
import java.nio.file.Path

/**
 * Licensing information resolver.
 *
 * @author Matouš Kučera
 */
interface LicenseResolver : OutputContainer<Path?> {
    /**
     * The resolver workspace.
     */
    val workspace: Workspace

    /**
     * The source where the license was acquired (a URL, a file path, ...).
     */
    val licenseSource: String?

    /**
     * The license file output of this resolver.
     */
    val licenseOutput: Output<out Path?>
}
