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

package me.kcra.takenaka.core

import java.io.File
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Options for resolvers, these are integers ORed together.
 */
typealias ResolverOptions = Int

/**
 * Creates resolver options from multiple options.
 *
 * @param options the options
 * @return the resolver options
 */
fun resolverOptionsOf(vararg options: Int): ResolverOptions = options.reduceOrNull { v1, v2 -> v1 or v2 } ?: 0

/**
 * Checks if the options contain an option.
 *
 * @param option the option to check
 * @return do the options contain the option?
 */
operator fun ResolverOptions.contains(option: Int): Boolean = (this and option) != 0

/**
 * Requests resolvers to cache items without a checksum.
 */
const val RELAXED_CACHE = 0x00000001
fun ResolverOptions.relaxedCache(): ResolverOptions = this or RELAXED_CACHE

/**
 * A filesystem-based workspace.
 */
interface Workspace {
    /**
     * The workspace root, this should never be navigated manually.
     */
    val rootDirectory: File

    /**
     * Options for resolvers.
     */
    val resolverOptions: ResolverOptions

    /**
     * Converts this workspace to a versioned one.
     *
     * @param version the version to which the workspace should belong
     * @return the versioned workspace
     */
    fun asVersioned(version: Version): VersionedWorkspace =
        VersionedWorkspace(rootDirectory, resolverOptions, version)

    /**
     * Checks if this workspace contains the specified file.
     *
     * @param file the file name
     * @return does this workspace contain the file?
     */
    operator fun contains(file: String): Boolean = rootDirectory.resolve(file).isFile

    /**
     * Resolves a file in this workspace.
     *
     * @param file the file name
     * @return the file
     */
    operator fun get(file: String): File = rootDirectory.resolve(file)
}

/**
 * A workspace, which houses multiple sub-workspaces.
 *
 * @property rootDirectory the workspace root
 */
class CompositeWorkspace(override val rootDirectory: File, override val resolverOptions: ResolverOptions = resolverOptionsOf()) : Workspace {
    init {
        rootDirectory.mkdirs()
    }

    /**
     * Creates a new versioned sub-workspace.
     *
     * @param version the version to which the sub-workspace should belong
     * @return the sub-workspace
     */
    fun versioned(version: Version): VersionedWorkspace =
        VersionedWorkspace(rootDirectory.resolve(version.id), resolverOptions, version)
}

/**
 * A workspace, which belongs to a specific Minecraft version.
 *
 * @property rootDirectory the workspace root
 * @property version the version which this workspace belongs to
 */
class VersionedWorkspace(override val rootDirectory: File, override val resolverOptions: ResolverOptions = resolverOptionsOf(), val version: Version) : Workspace {
    internal val spigotManifestLock: Lock = ReentrantLock()
    internal val mojangManifestLock: Lock = ReentrantLock()

    init {
        rootDirectory.mkdirs()
    }

    override fun asVersioned(version: Version): VersionedWorkspace = this
}
