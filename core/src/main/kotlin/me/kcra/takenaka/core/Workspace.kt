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
import java.nio.file.Path
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.*
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Boolean options for workspace consumers, these are integers ORed together.
 */
typealias WorkspaceOptions = Int

/**
 * A group of resolver options used in the core library.
 */
object DefaultWorkspaceOptions {
    /**
     * Requests resolvers to cache items without a checksum.
     */
    const val RELAXED_CACHE = 0x00000001
}

/**
 * Checks if the options contain an option.
 *
 * @param option the option to check
 * @return do the options contain the option?
 */
operator fun WorkspaceOptions.contains(option: Int): Boolean = (this and option) != 0

/**
 * Creates workspace options from multiple options.
 *
 * @param options the options
 * @return the resolver options
 */
fun workspaceOptionsOf(vararg options: Int): WorkspaceOptions = options.reduceOrNull { v1, v2 -> v1 or v2 } ?: 0

/**
 * A builder for [WorkspaceOptions].
 *
 * @property value the integer value, internal use only (for adding additional resolver options)
 * @author Matouš Kučera
 */
class WorkspaceOptionsBuilder(var value: WorkspaceOptions = 0) {
    /**
     * Appends the [DefaultWorkspaceOptions.RELAXED_CACHE] option.
     */
    fun relaxedCache() {
        value = value or DefaultWorkspaceOptions.RELAXED_CACHE
    }

    /**
     * Checks if the options contain an option.
     *
     * @param option the option to check
     * @return do the options contain the option?
     */
    operator fun contains(option: Int): Boolean = (value and option) != 0

    /**
     * Returns the workspace options.
     *
     * @return the value
     */
    fun toWorkspaceOptions(): WorkspaceOptions = value
}

/**
 * Creates workspace options from a builder.
 *
 * @param block the builder action
 * @return the workspace options
 */
inline fun buildWorkspaceOptions(block: WorkspaceOptionsBuilder.() -> Unit): WorkspaceOptions =
    WorkspaceOptionsBuilder().apply(block).toWorkspaceOptions()

/**
 * A filesystem-based workspace.
 */
interface Workspace {
    /**
     * The workspace root, this should never be navigated manually.
     */
    val rootDirectory: Path

    /**
     * Workspace options.
     */
    val options: WorkspaceOptions

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
     * Acquires a workspace-level lock by a key.
     *
     * @param key the lock key
     * @param block the lambda, which will be executed when the lock is acquired
     */
    fun <T> withLock(key: Any, block: () -> T): T

    /**
     * Converts this workspace to a composite one.
     *
     * @return the composite workspace
     */
    fun asComposite(): CompositeWorkspace

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
     * A delegation alias for [asComposite].
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): CompositeWorkspace = asComposite()
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
     * The resolver options.
     */
    var options = 0

    /**
     * Sets the root directory.
     *
     * @param path the root directory path
     */
    fun rootDirectory(path: String) {
        rootDirectory(Path(path))
    }

    /**
     * Sets the root directory.
     *
     * @param file the root directory
     */
    fun rootDirectory(file: File) {
        rootDirectory(file.toPath())
    }

    /**
     * Sets the root directory.
     *
     * @param path the root directory path
     */
    fun rootDirectory(path: Path) {
        rootDirectory = path
    }

    /**
     * Sets [options].
     *
     * @param value the options
     */
    fun options(value: WorkspaceOptions) {
        options = value
    }

    /**
     * Sets [options].
     *
     * @param values the options
     */
    fun options(vararg values: WorkspaceOptions) {
        options = workspaceOptionsOf(*values)
    }

    /**
     * Builds and sets [options] using [block].
     *
     * @param block the builder action
     */
    inline fun options(block: WorkspaceOptionsBuilder.() -> Unit) {
        options = buildWorkspaceOptions(block)
    }

    /**
     * Creates a workspace from this builder.
     *
     * @return the simple workspace
     */
    open fun toWorkspace(): Workspace = Simple(rootDirectory, options)
}

/**
 * A simple workspace.
 */
private class Simple(override val rootDirectory: Path, override val options: WorkspaceOptions = workspaceOptionsOf()) : Workspace {
    private val composite by lazy { CompositeWorkspace(rootDirectory, options, locks) }
    private val locks = mutableMapOf<Any, Lock>()

    init {
        rootDirectory.createDirectories()
    }

    override fun <T> withLock(key: Any, block: () -> T): T {
        val keyedLock = synchronized(this) {
            locks.getOrPut(key, ::ReentrantLock)
        }

        return keyedLock.withLock(block)
    }
    override fun asComposite() = composite
}

/**
 * Creates a workspace from a builder.
 *
 * @param block the builder action
 * @return the workspace
 */
inline fun workspace(block: WorkspaceBuilder.() -> Unit): Workspace =
    WorkspaceBuilder().apply(block).toWorkspace()

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
    override fun toWorkspace() = CompositeWorkspace(rootDirectory, options)
}

/**
 * Creates a composite workspace from a builder.
 *
 * @param block the builder action
 * @return the workspace
 */
inline fun compositeWorkspace(block: CompositeWorkspaceBuilder.() -> Unit): CompositeWorkspace =
    CompositeWorkspaceBuilder().apply(block).toWorkspace()

/**
 * A workspace, which houses multiple sub-workspaces.
 *
 * @property rootDirectory the workspace root
 * @property options the resolver options
 */
class CompositeWorkspace(
    override val rootDirectory: Path,
    override val options: WorkspaceOptions = workspaceOptionsOf(),
    private val locks: MutableMap<Any, Lock> = mutableMapOf()
) : Workspace {
    init {
        rootDirectory.createDirectories()
    }

    override fun <T> withLock(key: Any, block: () -> T): T {
        val keyedLock = synchronized(this) {
            locks.getOrPut(key, ::ReentrantLock)
        }

        return keyedLock.withLock(block)
    }
    override fun asComposite() = this

    /**
     * Creates a new sub-workspace with a unique name.
     *
     * @return the sub-workspace
     */
    inline fun createWorkspace(crossinline block: MemberBuilder.() -> Unit): Workspace =
        MemberBuilder(this).apply(block).toWorkspace()

    /**
     * Creates a new composite sub-workspace with a unique name.
     *
     * @return the sub-workspace
     */
    inline fun createCompositeWorkspace(crossinline block: CompositeMemberBuilder.() -> Unit): CompositeWorkspace =
        CompositeMemberBuilder(this).apply(block).toWorkspace()

    /**
     * Creates a new versioned sub-workspace.
     *
     * @return the sub-workspace
     */
    inline fun createVersionedWorkspace(crossinline block: VersionedMemberBuilder.() -> Unit): VersionedWorkspace =
        VersionedMemberBuilder(this).apply(block).toWorkspace()

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
            get() = rootDirectory.relativize(parent.rootDirectory).pathString
            set(value) {
                rootDirectory = parent[value]
            }

        init {
            options = parent.options
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
            get() = rootDirectory.relativize(parent.rootDirectory).pathString
            set(value) {
                rootDirectory = parent[value]
            }

        init {
            options = parent.options
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
            get() = rootDirectory_ ?: parent[version.id]
            set(value) {
                rootDirectory_ = value
            }

        init {
            options = parent.options
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
    override fun toWorkspace() = VersionedWorkspace(rootDirectory, options, version)
}

/**
 * Creates a versioned workspace from a builder.
 *
 * @param block the builder action
 * @return the workspace
 */
inline fun versionedWorkspace(block: VersionedWorkspaceBuilder.() -> Unit): VersionedWorkspace =
    VersionedWorkspaceBuilder().apply(block).toWorkspace()

/**
 * A workspace, which belongs to a specific Minecraft version.
 *
 * @property rootDirectory the workspace root
 * @property options the resolver options
 * @property version the version which this workspace belongs to
 */
class VersionedWorkspace(override val rootDirectory: Path, override val options: WorkspaceOptions = workspaceOptionsOf(), val version: Version) : Workspace {
    private val composite by lazy { CompositeWorkspace(rootDirectory, options, locks) }
    private val locks = mutableMapOf<Any, Lock>()

    init {
        rootDirectory.createDirectories()
    }

    override fun <T> withLock(key: Any, block: () -> T): T {
        val keyedLock = synchronized(this) {
            locks.getOrPut(key, ::ReentrantLock)
        }

        return keyedLock.withLock(block)
    }
    override fun asComposite() = composite
}
