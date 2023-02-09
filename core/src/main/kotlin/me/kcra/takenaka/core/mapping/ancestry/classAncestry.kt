package me.kcra.takenaka.core.mapping.ancestry

import me.kcra.takenaka.core.Version
import net.fabricmc.mappingio.tree.MappingTree
import java.util.Collections

typealias VersionedMappings = Map<Version, MappingTree>

class ClassAncestryTree(val allowedNamespaces: MutableMap<Version, Collection<Int>> = mutableMapOf()) : MutableList<ClassAncestryNode> by mutableListOf()

data class ClassAncestryNode(
    val keys: MutableSet<String> = mutableSetOf(),
    val mappings: MutableMap<Version, MappingTree.ClassMapping> = mutableMapOf()
)

fun classAncestryTreeOf(mappings: VersionedMappings, allowedNamespaces: Collection<String> = emptyList()): ClassAncestryTree {
    val classTree = ClassAncestryTree()

    mappings.forEach { (version, tree) ->
        var treeAllowedNamespaces = allowedNamespaces
            .map(tree::getNamespaceId)
            .filter { it != MappingTree.NULL_NAMESPACE_ID }

        if (treeAllowedNamespaces.isEmpty()) {
            treeAllowedNamespaces = (0 until tree.maxNamespaceId).toList()
        }

        classTree.allowedNamespaces[version] = treeAllowedNamespaces

        tree.classes.forEach { klass ->
            val classMappings = treeAllowedNamespaces.mapNotNull(klass::getDstName)

            val node = classTree.firstOrNull { !Collections.disjoint(it.keys, classMappings) }
                ?: ClassAncestryNode().also { classTree += it }

            node.keys += classMappings
            node.mappings[version] = klass
        }
    }

    return classTree
}
