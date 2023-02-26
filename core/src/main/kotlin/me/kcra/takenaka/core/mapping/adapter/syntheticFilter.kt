package me.kcra.takenaka.core.mapping.adapter

import org.objectweb.asm.Opcodes
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.mapping.resolve.modifiers
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

private val logger = KotlinLogging.logger {}

/**
 * Filters out synthetic classes and members.
 *
 * This filter relies on the presence of [VanillaMappingContributor.NS_MODIFIERS], so make sure you visit [VanillaMappingContributor] beforehand.
 */
fun MappingTree.filterNonSynthetic() {
    val namespaceId = getNamespaceId(VanillaMappingContributor.NS_MODIFIERS)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Mapping tree has not visited modifiers before")
    }

    val classesForRemoval = mutableListOf<String>()
    classes.forEach { klass ->
        if ((Opcodes.ACC_SYNTHETIC and klass.modifiers) != 0) {
            logger.debug { "removed class ${klass.srcName}, synthetic" }
            classesForRemoval += klass.srcName
            return@forEach
        }

        val fieldsForRemoval = mutableMapOf<String, String>()
        klass.fields.forEach { field ->
            if ((Opcodes.ACC_SYNTHETIC and field.modifiers) != 0) {
                logger.debug { "removed field ${klass.srcName}#${field.srcName} ${field.srcDesc}, synthetic" }
                fieldsForRemoval += field.srcName to field.srcDesc
            }
        }
        fieldsForRemoval.forEach { (name, desc) -> klass.removeField(name, desc) }
        logger.debug { "removed ${fieldsForRemoval.size} synthetic field(s) in class ${klass.srcName}" }

        val methodsForRemoval = mutableMapOf<String, String>()
        klass.methods.forEach { method ->
            if ((Opcodes.ACC_SYNTHETIC and method.modifiers) != 0) {
                logger.debug { "removed method ${klass.srcName}#${method.srcName}${method.srcDesc}, synthetic" }
                methodsForRemoval += method.srcName to method.srcDesc
            }
        }
        methodsForRemoval.forEach { (name, desc) -> klass.removeMethod(name, desc) }
        logger.debug { "removed ${methodsForRemoval.size} synthetic method(s) in class ${klass.srcName}" }
    }

    classesForRemoval.forEach { removeClass(it) }
    logger.info { "removed ${classesForRemoval.size} synthetic class(es)" }
}
