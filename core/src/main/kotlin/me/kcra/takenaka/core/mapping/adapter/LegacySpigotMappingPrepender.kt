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

package me.kcra.takenaka.core.mapping.adapter

import me.kcra.takenaka.core.mapping.toInternalName
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import org.objectweb.asm.commons.Remapper

/**
 * A mapping visitor that prepends an implicit `net.minecraft.server.VVV` package prefix, if needed.
 *
 * Spigot mappings have had an implicit `net.minecraft.server.VVV` package, `VVV` being the CraftBukkit NMS version (e.g. 1_19_R2, you can get this from the CraftBukkit POM), until 1.17.
 * In one of the last 1.16.5 BuildData commits, packages were added to the mappings, even though they are not present in the distributed reobfuscated JARs.
 * To solve this, you can apply [LegacySpigotMappingPrepender] instances to both a [me.kcra.takenaka.core.mapping.resolve.SpigotClassMappingResolver] and a [me.kcra.takenaka.core.mapping.resolve.SpigotMemberMappingResolver],
 * passing the same instance of [prependedClasses] to both.
 *
 * @param namespace the namespace, whose mappings are to be modified (most likely `spigot` or to be exact, [me.kcra.takenaka.core.mapping.resolve.AbstractSpigotMappingResolver.targetNamespace])
 * @param prependedClasses the classes that have been prepended and should be remapped in descriptors (only class names that are a package-replace candidate should be specified here, any class name without a package will have a prefix prepended implicitly)
 * @param next the visitor to delegate to
 * @author Matouš Kučera
 */
class LegacySpigotMappingPrepender(next: MappingVisitor, val namespace: String = "spigot", val prependedClasses: MutableList<String> = mutableListOf()) : ForwardingMappingVisitor(next) {
    private val remapper = PrependingRemapper(prependedClasses = prependedClasses)

    private var dstNamespaces: List<String> = emptyList()

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        this.dstNamespaces = dstNamespaces

        super.visitNamespaces(srcNamespace, dstNamespaces)
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        var name0 = name

        if (name0 != null && targetKind == MappedElementKind.CLASS && dstNamespaces[namespace] == this.namespace) {
            name0 = name0.toInternalName()

            prependedClasses += name0
            name0 = remapper.map(name0)
        }

        super.visitDstName(targetKind, namespace, name0)
    }

    override fun visitDstDesc(targetKind: MappedElementKind, namespace: Int, desc: String?) {
        var desc0 = desc

        if (desc0 != null && dstNamespaces[namespace] == this.namespace) {
            desc0 = remapper.mapDesc(desc0)
        }

        super.visitDstDesc(targetKind, namespace, desc0)
    }

    /**
     * A [Remapper] that prepends class names with `net.minecraft.server.VVV`.
     *
     * @param prependedClasses the classes that have been prepended and should be remapped in descriptors (only class names that are a package-replace candidate should be specified here, any class name without a package will have a prefix prepended implicitly)
     */
    class PrependingRemapper(val prependedClasses: List<String> = emptyList()) : Remapper() {
        override fun map(internalName: String): String {
            if (!internalName.contains('/') || internalName in prependedClasses) {
                return "net/minecraft/server/VVV/${internalName.substringAfterLast('/')}"
            }
            return internalName
        }
    }

    companion object {
        /**
         * A default instance.
         */
        val PREPENDING_REMAPPER = PrependingRemapper()
    }
}
