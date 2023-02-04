package me.kcra.takenaka.core.mapping

import me.kcra.takenaka.core.Version
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree

/**
 * A mapping tree with a version.
 *
 * @property version the version
 * @author Matouš Kučera
 */
class VersionedMappingFile(
    val version: Version,
    tree: MemoryMappingTree = MemoryMappingTree()
) : MappingTree by tree, MappingVisitor by tree
