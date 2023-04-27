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

package me.kcra.takenaka.core.test

import me.kcra.takenaka.core.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceTest {
    @Test
    fun `when creating a simple workspace with default resolver options, then the resolverOptions property should be 0`() {
        val workspace = workspace {
            rootDirectory("simple")
        }
        assertEquals(0, workspace.options)

        workspace.remove()
    }

    @Test
    fun `when creating a simple workspace with custom resolver options, then the resolverOptions property should reflect the custom options`() {
        val workspace = workspace {
            rootDirectory("simple")
            options {
                relaxedCache()
            }
        }
        assertTrue(workspace.options.contains(DefaultWorkspaceOptions.RELAXED_CACHE))

        workspace.remove()
    }

    @Test
    fun `when creating a composite workspace, then its rootDirectory and resolverOptions should be set to the values from the builder`() {
        val workspace = compositeWorkspace {
            rootDirectory("composite")
            options = DefaultWorkspaceOptions.RELAXED_CACHE
        }
        assertEquals("composite", workspace.rootDirectory.toString())
        assertEquals(DefaultWorkspaceOptions.RELAXED_CACHE, workspace.options)

        workspace.remove()
    }

    @Test
    fun `when calling clean() on a workspace, then the root directory should be deleted and recreated`() {
        val workspace = workspace {
            rootDirectory("simple")
        }
        workspace["file"].writeText("content")
        assertTrue(workspace.rootDirectory.isDirectory())
        assertEquals(1, workspace.rootDirectory.directoryEntryCount)
        workspace.clean()
        assertTrue(workspace.rootDirectory.isDirectory())
        assertEquals(0, workspace.rootDirectory.directoryEntryCount)

        workspace.remove()
    }

    @Test
    fun `when checking if a workspace contains a file, then it should return true if the file exists in the workspace`() {
        val workspace = workspace {
            rootDirectory("simple")
        }
        workspace["file"].writeText("content")
        assertTrue("file" in workspace)

        workspace.remove()
    }

    @Test
    fun `when checking if a workspace contains a file, then it should return false if the file does not exist in the workspace`() {
        val workspace = workspace {
            rootDirectory("simple")
        }
        assertFalse("file" in workspace)

        workspace.remove()
    }

    @Test
    fun `when resolving a file in a workspace, then it should return the path to the file in the workspace`() {
        val workspace = workspace {
            rootDirectory("simple")
        }
        workspace["file"].writeText("content")
        assertEquals(Path("simple/file"), workspace["file"])

        workspace.remove()
    }

    @Test
    fun `when building resolver options with multiple options, then the resulting options should contain all the specified options`() {
        val options = workspaceOptionsOf(DefaultWorkspaceOptions.RELAXED_CACHE, 2)
        assertTrue(options.contains(DefaultWorkspaceOptions.RELAXED_CACHE))
        assertTrue(options.contains(2))
    }

    @Test
    fun `when building resolver options with a single option, then the resulting options should contain the specified option`() {
        val options = workspaceOptionsOf(DefaultWorkspaceOptions.RELAXED_CACHE)
        assertTrue(options.contains(DefaultWorkspaceOptions.RELAXED_CACHE))
    }

    @Test
    fun `when checking if resolver options contain an option, then it should return true if the options contain the option`() {
        val options = workspaceOptionsOf(DefaultWorkspaceOptions.RELAXED_CACHE, 2)
        assertTrue(options.contains(2))
    }

    @Test
    fun `when checking if resolver options contain an option, then it should return false if the options do not contain the option`() {
        val options = workspaceOptionsOf(DefaultWorkspaceOptions.RELAXED_CACHE, 2)
        assertFalse(options.contains(4))
    }

    /**
     * Removes the workspace from the filesystem, useful for cleaning up after tests.
     */
    private fun Workspace.remove() {
        // TODO: remove toFile() when the kotlin.io.path API is no longer experimental
        rootDirectory.toFile().deleteRecursively()
    }

    /**
     * Counts the entries (files, directories) in this directory.
     */
    private val Path.directoryEntryCount: Int
        get() = listDirectoryEntries().size
}
