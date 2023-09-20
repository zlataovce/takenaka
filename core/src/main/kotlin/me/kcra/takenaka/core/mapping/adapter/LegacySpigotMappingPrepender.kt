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

import me.kcra.takenaka.core.mapping.MappingContributor
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.toInternalName
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.commons.Remapper

/**
 * A mapping visitor that prepends an implicit `net.minecraft.server.VVV` package prefix, if needed.
 *
 * Spigot mappings have had an implicit `net.minecraft.server.VVV` package, `VVV` being the CraftBukkit NMS version (e.g. 1_19_R2, you can get this from the CraftBukkit POM), until 1.17.
 * In one of the last 1.16.5 BuildData commits, packages were added to the mappings, even though they are not present in the distributed reobfuscated JARs.
 * To solve this, you can apply [LegacySpigotMappingPrepender] instances to both a [me.kcra.takenaka.core.mapping.resolve.impl.SpigotClassMappingResolver] and a [me.kcra.takenaka.core.mapping.resolve.impl.SpigotMemberMappingResolver],
 * using a linked prepender for the member resolver ([createLinked] or [Link]).
 *
 * @param next the visitor to delegate to
 * @property namespace the namespace, whose mappings are to be modified (most likely `spigot` or to be exact, [me.kcra.takenaka.core.mapping.resolve.impl.AbstractSpigotMappingResolver.targetNamespace])
 * @property prependEverything whether every class should have its package replaced (useful for fixing 1.16.5), defaults to false
 * @property prependedClasses the classes that have been prepended and should be remapped in descriptors (only class names that are a package-replace candidate should be specified here, any class name without a package will have a prefix prepended implicitly)
 * @author Matouš Kučera
 */
class LegacySpigotMappingPrepender private constructor(
    next: MappingVisitor,
    val namespace: String,
    val prependEverything: Boolean,
    private val prependedClasses: MutableSet<String>
) : ForwardingMappingVisitor(next) {
    /**
     * The remapper.
     */
    private val remapper = PrependingRemapper(prependedClasses, prependEverything)

    /**
     * The [namespace]'s ID.
     */
    private var namespaceId = MappingTreeView.NULL_NAMESPACE_ID

    /**
     * Constructs a new [LegacySpigotMappingPrepender].
     *
     * @param next the visitor to delegate to
     * @param namespace the namespace, whose mappings are to be modified
     * @param prependEverything whether every class should have its package replaced, defaults to false
     */
    constructor(next: MappingVisitor, namespace: String = "spigot", prependEverything: Boolean = false)
            : this(next, namespace, prependEverything, mutableSetOf())

    /**
     * Creates a new linked prepender.
     *
     * @param next the visitor to delegate to, uses the same one by default
     * @param prependEverything whether every class should have its package replaced, defaults to false
     * @return the linked prepender
     */
    fun createLinked(next: MappingVisitor = this.next, prependEverything: Boolean = false): LegacySpigotMappingPrepender {
        return LegacySpigotMappingPrepender(next, this.namespace, prependEverything, this.prependedClasses)
    }

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        val nsId = dstNamespaces.indexOf(this.namespace)
        if (nsId != -1) {
            this.namespaceId = nsId
        }

        super.visitNamespaces(srcNamespace, dstNamespaces)
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        var name0 = name
        if (name0 != null && targetKind == MappedElementKind.CLASS && this.namespaceId == namespace) {
            name0 = remapper.map(name0.toInternalName())
        }

        super.visitDstName(targetKind, namespace, name0)
    }

    override fun visitDstDesc(targetKind: MappedElementKind, namespace: Int, desc: String?) {
        var desc0 = desc
        if (desc0 != null && this.namespaceId == namespace) {
            desc0 = remapper.mapDesc(desc0)
        }

        super.visitDstDesc(targetKind, namespace, desc0)
    }

    /**
     * A [Remapper] that prepends class names with `net.minecraft.server.VVV`.
     *
     * @param prependedClasses the classes that have been prepended and should be remapped in descriptors (only class names that are a package-replace candidate should be specified here, any class name without a package will have a prefix prepended implicitly)
     * @param prependEverything whether every class should have its package replaced (useful for fixing 1.16.5)
     */
    class PrependingRemapper(val prependedClasses: MutableSet<String>, val prependEverything: Boolean) : Remapper() {
        override fun map(internalName: String): String {
            val isPrependable = '/' !in internalName
            if (isPrependable) {
                prependedClasses += internalName
            }

            if (prependEverything || (isPrependable || internalName in prependedClasses)) {
                return "net/minecraft/server/VVV/${internalName.substringAfterLast('/')}"
            }
            return internalName
        }
    }

    /**
     * A utility class for linking prependers together in the case of multiple resolvers.
     */
    class Link {
        /**
         * Currently prepended classes - the linking point.
         */
        private val prependedClasses = mutableSetOf<String>()

        /**
         * Creates a new linked prepender.
         *
         * @param next the visitor to delegate to, uses the same one by default
         * @param namespace the namespace, whose mappings are to be modified; defaults to `spigot`
         * @param prependEverything whether every class should have its package replaced, defaults to false
         * @return the linked prepender
         */
        fun createLinked(next: MappingVisitor, namespace: String = "spigot", prependEverything: Boolean = false): LegacySpigotMappingPrepender {
            return LegacySpigotMappingPrepender(next, namespace, prependEverything, this.prependedClasses)
        }

        /**
         * Creates a new prepending contributor with a linked prepender.
         *
         * @param contributor the mapping contributor to wrap
         * @param prependEverything whether every class should have its package replaced, defaults to false
         * @return the prepending contributor
         */
        fun createPrependingContributor(contributor: MappingContributor, prependEverything: Boolean = false): MappingContributor {
            return WrappingContributor(contributor) {
                createLinked(it, contributor.targetNamespace, prependEverything)
            }
        }
    }
}
