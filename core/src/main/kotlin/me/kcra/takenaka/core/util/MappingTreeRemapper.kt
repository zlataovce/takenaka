package me.kcra.takenaka.core.util

import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.commons.Remapper

typealias MappingSelector = (MappingTree.ElementMapping) -> String?

/**
 * A [Remapper] implementation that remaps class and class member names from a mapping tree.
 *
 * @param tree the mapping tree
 * @param mappingSelector a function that selects the desired mapping for the element
 */
class MappingTreeRemapper(val tree: MappingTree, val mappingSelector: MappingSelector) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String =
        tree.getClass(owner)?.getMethod(name, descriptor)?.let(mappingSelector) ?: name
    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String =
        mapFieldName(owner, name, descriptor)
    override fun mapFieldName(owner: String, name: String, descriptor: String): String =
        tree.getClass(owner)?.getField(name, descriptor)?.let(mappingSelector) ?: name
    override fun map(internalName: String): String = tree.getClass(internalName)?.let(mappingSelector) ?: internalName
}