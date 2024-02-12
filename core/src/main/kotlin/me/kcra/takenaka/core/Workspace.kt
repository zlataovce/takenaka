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
 * A filesystem-based workspace.
 */
interface Workspace {
    /**
     * The workspace root, this should never be navigated manually.
     */
    val rootDirectory: Path

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
     * Creates a workspace from this builder.
     *
     * @return the simple workspace
     */
    open fun toWorkspace(): Workspace = Simple(rootDirectory)
}

/**
 * A simple workspace.
 */
private class Simple(override val rootDirectory: Path) : Workspace {
    private val composite by lazy { CompositeWorkspace(rootDirectory, locks) }
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
    override fun toWorkspace() = CompositeWorkspace(rootDirectory)
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
 */
class CompositeWorkspace(override val rootDirectory: Path, private val locks: MutableMap<Any, Lock> = mutableMapOf()) : Workspace {
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
    override fun toWorkspace() = VersionedWorkspace(rootDirectory, version)
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
 * @property version the version which this workspace belongs to
 */
class VersionedWorkspace(override val rootDirectory: Path, val version: Version) : Workspace {
    private val composite by lazy { CompositeWorkspace(rootDirectory, locks) }
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
