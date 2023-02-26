package me.kcra.takenaka.core.mapping.adapter

import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

private val logger = KotlinLogging.logger {}

/**
 * Filters out static initializers (&lt;clinit&gt;).
 */
fun MappingTree.filterNonStaticInitializer() {
    classes.forEach { klass ->
        val clinit = klass.methods.firstOrNull { it.srcName == "<clinit>" } ?: return@forEach

        logger.debug { "removed static initializer of ${klass.srcName}" }
        klass.removeMethod(clinit.srcName, clinit.srcDesc)
    }
}
