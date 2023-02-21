package me.kcra.takenaka.core.mapping.adapter

import me.kcra.takenaka.core.mapping.toInternalName
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import org.objectweb.asm.commons.Remapper

/**
 * A mapping visitor that prepends an implicit `net.minecraft.server.VVV` package prefix, if needed.
 *
 * NOTE: the "Legacy" part of the class name is not related to [The Flattening](https://minecraft.fandom.com/wiki/Java_Edition_1.13/Flattening).
 *
 * @param dstNamespace the namespace, whose mappings are to be modified
 * @param prependAll whether every class name should be prefixed (or have their package replaced), only package-less class names are prefixed by default
 * @param next the visitor to delegate to
 * @author Matouš Kučera
 */
class LegacySpigotMappingPrepender(next: MappingVisitor, prependAll: Boolean = false, val dstNamespace: String = "spigot") : ForwardingMappingVisitor(next) {
    private val remapper = PrependingRemapper(prependAll)

    private var srcNamespace: String? = null
    private var dstNamespaces: MutableList<String>? = null

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        this.srcNamespace = srcNamespace
        this.dstNamespaces = dstNamespaces

        super.visitNamespaces(srcNamespace, dstNamespaces)
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        var name0 = name

        if (name0 != null && targetKind == MappedElementKind.CLASS && dstNamespaces?.get(namespace) == dstNamespace) {
            name0 = remapper.map(name0.toInternalName())
        }

        super.visitDstName(targetKind, namespace, name0)
    }

    override fun visitDstDesc(targetKind: MappedElementKind, namespace: Int, desc: String?) {
        var desc0 = desc

        if (desc0 != null && dstNamespaces?.get(namespace) == dstNamespace) {
            desc0 = remapper.mapDesc(desc0)
        }

        super.visitDstDesc(targetKind, namespace, desc0)
    }

    /**
     * A [Remapper] that prepends class names with `net.minecraft.server.VVV`.
     *
     * @param remapAll whether every class name should be prefixed (or have their package replaced), only package-less class names are prefixed by default
     */
    class PrependingRemapper(val remapAll: Boolean = false) : Remapper() {
        override fun map(internalName: String): String {
            if ((remapAll && (internalName.startsWith("net/minecraft") || internalName.startsWith("com/mojang"))) || !internalName.contains('/')) {
                return "net/minecraft/server/VVV/${internalName.substringAfterLast('/')}"
            }
            return internalName
        }
    }

    companion object {
        /**
         * A default instance.
         */
        val PREPENDING_REMAPPER = PrependingRemapper(remapAll = true)
    }
}
