package me.kcra.takenaka.core.mapping.resolve

import mu.KotlinLogging
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor

private val logger = KotlinLogging.logger {}

/**
 * A mapping visitor that skips field and method mappings with a null descriptor.
 * This is adapted from the mapping-io [net.fabricmc.mappingio.adapter.MissingDescFilter] class, this one includes logging on top.
 *
 * @param next the visitor to delegate to
 * @author Matouš Kučera
 */
class MissingDescriptorFilter(next: MappingVisitor) : ForwardingMappingVisitor(next) {
    private var currentClass: String? = null

    override fun visitClass(srcName: String?): Boolean {
        currentClass = srcName

        return super.visitClass(srcName)
    }

    override fun visitField(srcName: String?, srcDesc: String?): Boolean {
        if (srcDesc == null) {
            logger.debug { "ignored null descriptor of field $srcName in class $currentClass" }
            return false
        }

        return super.visitField(srcName, srcDesc)
    }

    override fun visitMethod(srcName: String?, srcDesc: String?): Boolean {
        if (srcDesc == null) {
            logger.debug { "ignored null descriptor of method $srcName in class $currentClass" }
            return false
        }

        return super.visitMethod(srcName, srcDesc)
    }
}
