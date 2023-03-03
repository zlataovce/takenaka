package me.kcra.takenaka.core.mapping.adapter

import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

private val logger = KotlinLogging.logger {}

/**
 * Corrects missing inner/anonymous class mappings.
 *
 * This method works on the principle of presuming that the owner part of the inner/anonymous class name should be completed with the owner's mapping.
 *
 * @param namespace the namespace, whose mappings are to be modified
 */
fun MappingTree.completeInnerClassNames(namespace: String) {
    val namespaceId = getNamespaceId(namespace)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Namespace is not present in the mapping tree")
    }

    var completionCount = 0
    classes.forEach { klass ->
        if ('$' !in klass.srcName || klass.getDstName(namespaceId) != null) return@forEach

        val owner = klass.srcName.substringBeforeLast('$')
        val name = klass.srcName.substringAfterLast('$')

        val ownerKlass = getClass(owner)
        if (ownerKlass == null) {
            logger.debug { "inner class ${klass.srcName} without owner for namespace $namespace" }
            return@forEach
        }

        val ownerName = ownerKlass.getDstName(namespaceId)
        if (ownerName == null) {
            logger.debug { "inner class ${klass.srcName} without mapped owner ${ownerKlass.srcName} for namespace $namespace" }
            return@forEach
        }

        klass.setDstName("$ownerName$$name", namespaceId)
        logger.debug { "completed inner class name ${klass.srcName} -> $ownerName$$name for namespace $namespace" }
        completionCount++
    }

    logger.info { "completed $completionCount inner class name(s) in namespace $namespace" }
}
