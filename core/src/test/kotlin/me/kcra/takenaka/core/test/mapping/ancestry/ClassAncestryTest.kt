package me.kcra.takenaka.core.test.mapping.ancestry

import me.kcra.takenaka.core.CompositeWorkspace
import me.kcra.takenaka.core.RELAXED_CACHE
import me.kcra.takenaka.core.manifestObjectMapper
import me.kcra.takenaka.core.mapping.ancestry.classAncestryTreeOf
import me.kcra.takenaka.core.resolverOptionsOf
import me.kcra.takenaka.core.test.mapping.resolve.resolveMappings
import java.io.File
import kotlin.test.Test

class ClassAncestryTest {
    private val objectMapper = manifestObjectMapper()
    private val workspaceDir = File("test-workspace")

    @Test
    fun `resolve mappings for supported versions and make an ancestry tree`() {
        val workspace = CompositeWorkspace(workspaceDir, resolverOptionsOf(RELAXED_CACHE))
        val mappings = workspace.resolveMappings(objectMapper, save = false)
        val tree = classAncestryTreeOf(mappings, allowedNamespaces = listOf("mojang", "searge", "intermediary", "spigot"))

        tree.forEach { node ->
            if (node.mappings.size == 1) {
                println("Node [${node.keys.joinToString(", ")}], 1 mapped version")
            }
        }
        println("Mapped ${tree.size} classes")
    }
}
