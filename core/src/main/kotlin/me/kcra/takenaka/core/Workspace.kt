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

import java.nio.file.Path
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.*
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Options for resolvers, these are integers ORed together.
 */
typealias ResolverOptions = Int

/**
 * Checks if the options contain an option.
 *
 * @param option the option to check
 * @return do the options contain the option?
 */
operator fun ResolverOptions.contains(option: Int): Boolean = (this and option) != 0

/**
 * Creates resolver options from multiple options.
 *
 * @param options the options
 * @return the resolver options
 */
fun resolverOptionsOf(vararg options: Int): ResolverOptions = options.reduceOrNull { v1, v2 -> v1 or v2 } ?: 0

/**
 * A builder for [ResolverOptions].
 *
 * @property value the integer value, internal use only (for adding additional resolver options)
 * @author Matouš Kučera
 */
class ResolverOptionsBuilder(var value: ResolverOptions = 0) {
    /**
     * Appends the [DefaultResolverOptions.RELAXED_CACHE] option.
     */
    fun relaxedCache() {
        value = value or DefaultResolverOptions.RELAXED_CACHE
    }

    /**
     * Checks if the options contain an option.
     *
     * @param option the option to check
     * @return do the options contain the option?
     */
    operator fun contains(option: Int): Boolean = (value and option) != 0

    /**
     * Returns the resolver options.
     *
     * @return the value
     */
    fun toResolverOptions(): ResolverOptions = value
}

/**
 * Creates resolver options from a builder.
 *
 * @param block the builder action
 * @return the resolver options
 */
inline fun buildResolverOptions(block: ResolverOptionsBuilder.() -> Unit): ResolverOptions = ResolverOptionsBuilder().apply(block).toResolverOptions()

/**
 * A group of resolver options used in the core library.
 */
object DefaultResolverOptions {
    /**
     * Requests resolvers to cache items without a checksum.
     */
    const val RELAXED_CACHE: ResolverOptions = 0x00000001
}

/**
 * A filesystem-based workspace.
 */
interface Workspace {
    /**
     * The workspace root, this should never be navigated manually.
     */
    val rootDirectory: Path

    /**
     * Options for resolvers.
     */
    val resolverOptions: ResolverOptions

    /**
     * Cleans this workspace.
     *
     * The default behavior is to completely remove the [rootDirectory] and recreate it.
     */
    fun clean() {
        // TODO: remove toFile() when the kotlin.io.path API is no longer experimental
        rootDirectory.toFile().deleteRecursively()
        rootDirectory.createDirectories()
    }

    /**
     * Checks if this workspace contains the specified file.
     *
     * @param file the file name
     * @return does this workspace contain the file?
     */
    operator fun contains(file: String): Boolean = rootDirectory.resolve(file).isRegularFile()

    /**
     * Resolves a file in this workspace.
     *
     * @param file the file name
     * @return the file
     */
    operator fun get(file: String): Path = rootDirectory.resolve(file)

    /**
     * Converts this workspace to a composite one.
     *
     * @return the composite workspace
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): CompositeWorkspace
}

/**
 * A workspace builder.
 *
 * @author Matouš Kučera
 */
open class WorkspaceBuilder {
    /**
     * The workspaces' root directory.
     */
    open var rootDirectory by Delegates.notNull<Path>()

    /**
     * Sets the root directory.
     * 
     * @param path the root directory path
     */
    fun rootDirectory(path: String) {
        rootDirectory = Path(path)
    }

    /**
     * The resolver options.
     */
    var resolverOptions = 0

    /**
     * Builds and sets [resolverOptions] using [block].
     *
     * @param block the builder action
     */
    inline fun resolverOptions(block: ResolverOptionsBuilder.() -> Unit) {
        resolverOptions = buildResolverOptions(block)
    }

    /**
     * Creates a workspace from this builder.
     *
     * @return the simple workspace
     */
    open fun toWorkspace(): Workspace = Simple(rootDirectory, resolverOptions)
}

/**
 * A simple workspace.
 */
private class Simple(override val rootDirectory: Path, override val resolverOptions: ResolverOptions = resolverOptionsOf()) : Workspace {
    private val composite by lazy { CompositeWorkspace(rootDirectory, resolverOptions) }

    init {
        rootDirectory.createDirectories()
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>) = composite
}

/**
 * Creates a workspace from a builder.
 *
 * @param block the builder action
 * @return the workspace
 */
inline fun workspace(block: WorkspaceBuilder.() -> Unit): Workspace = WorkspaceBuilder().apply(block).toWorkspace()

/**
 * A composite workspace builder.
 *
 * @author Matouš Kučera
 */
open class CompositeWorkspaceBuilder : WorkspaceBuilder() {
    /**
     * Creates a composite workspace from this builder.
     *
     * @return the composite workspace
     */
    override fun toWorkspace() = CompositeWorkspace(rootDirectory, resolverOptions)
}

/**
 * Creates a composite workspace from a builder.
 *
 * @param block the builder action
 * @return the workspace
 */
inline fun compositeWorkspace(block: CompositeWorkspaceBuilder.() -> Unit): CompositeWorkspace = CompositeWorkspaceBuilder().apply(block).toWorkspace()

/**
 * A workspace, which houses multiple sub-workspaces.
 *
 * @property rootDirectory the workspace root
 * @property resolverOptions the resolver options
 */
class CompositeWorkspace(override val rootDirectory: Path, override val resolverOptions: ResolverOptions = resolverOptionsOf()) : Workspace {
    init {
        rootDirectory.createDirectories()
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>) = this

    /**
     * Creates a new sub-workspace with a unique name.
     *
     * @return the sub-workspace
     */
    inline fun createWorkspace(crossinline block: MemberBuilder.() -> Unit): Workspace = MemberBuilder(this).apply(block).toWorkspace()

    /**
     * Creates a new composite sub-workspace with a unique name.
     *
     * @return the sub-workspace
     */
    inline fun createCompositeWorkspace(crossinline block: CompositeMemberBuilder.() -> Unit): CompositeWorkspace = CompositeMemberBuilder(this).apply(block).toWorkspace()

    /**
     * Creates a new versioned sub-workspace.
     *
     * @return the sub-workspace
     */
    inline fun createVersionedWorkspace(crossinline block: VersionedMemberBuilder.() -> Unit): VersionedWorkspace = VersionedMemberBuilder(this).apply(block).toWorkspace()

    /**
     * A base sub-workspace builder.
     *
     * @property parent the parent workspace
     */
    class MemberBuilder(val parent: CompositeWorkspace) : WorkspaceBuilder() {
        /**
         * The workspace name.
         */
        var name: String
            get() = rootDirectory.name
            set(value) {
                rootDirectory = parent.rootDirectory.resolve(value)
            }

        init {
            resolverOptions = parent.resolverOptions
        }
    }

    /**
     * A composite sub-workspace builder.
     *
     * @property parent the parent workspace
     */
    class CompositeMemberBuilder(val parent: CompositeWorkspace) : CompositeWorkspaceBuilder() {
        /**
         * The workspace name.
         */
        var name: String
            get() = rootDirectory.name
            set(value) {
                rootDirectory = parent.rootDirectory.resolve(value)
            }

        init {
            resolverOptions = parent.resolverOptions
        }
    }

    /**
     * A versioned sub-workspace builder.
     *
     * @property parent the parent workspace
     */
    class VersionedMemberBuilder(val parent: CompositeWorkspace) : VersionedWorkspaceBuilder() {
        /**
         * The user-defined root directory.
         */
        private var rootDirectory_: Path? = null

        /**
         * Returns the user-defined root directory or a directory named by the version.
         */
        override var rootDirectory: Path
            get() = rootDirectory_ ?: parent.rootDirectory.resolve(version.id)
            set(value) {
                rootDirectory_ = value
            }

        init {
            resolverOptions = parent.resolverOptions
        }
    }
}

/**
 * A versioned workspace builder.
 */
open class VersionedWorkspaceBuilder : WorkspaceBuilder() {
    /**
     * The version.
     */
    var version by Delegates.notNull<Version>()

    /**
     * Creates a versioned workspace from this builder.
     *
     * @return the versioned workspace
     */
    override fun toWorkspace() = VersionedWorkspace(rootDirectory, resolverOptions, version)
}

/**
 * Creates a versioned workspace from a builder.
 *
 * @param block the builder action
 * @return the workspace
 */
inline fun versionedWorkspace(block: VersionedWorkspaceBuilder.() -> Unit): VersionedWorkspace = VersionedWorkspaceBuilder().apply(block).toWorkspace()

/**
 * A workspace, which belongs to a specific Minecraft version.
 *
 * @property rootDirectory the workspace root
 * @property resolverOptions the resolver options
 * @property version the version which this workspace belongs to
 */
class VersionedWorkspace(override val rootDirectory: Path, override val resolverOptions: ResolverOptions = resolverOptionsOf(), val version: Version) : Workspace {
    private val composite by lazy { CompositeWorkspace(rootDirectory, resolverOptions) }
    internal val spigotManifestLock: Lock = ReentrantLock()
    internal val mojangManifestLock: Lock = ReentrantLock()
    internal val yarnMetadataLock: Lock = ReentrantLock()

    init {
        rootDirectory.createDirectories()
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>) = composite
}
