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

import me.kcra.takenaka.core.mapping.resolve.impl.AbstractSpigotMappingResolver
import me.kcra.takenaka.core.mapping.resolve.impl.craftBukkitNmsVersion
import net.fabricmc.mappingio.tree.MappingTree

private val CB_VERSION_REGEX = "/\\d+_\\d+_R\\d+/".toRegex()

/**
 * Removes the NMS version string of a CraftBukkit class name.
 *
 * @return the string with the replacement
 */
fun String.normalizeCraftBukkitName(): String = replace(CB_VERSION_REGEX, "/VVV/")

/**
 * Replaces `VVV` in Spigot mappings for the appropriate CraftBukkit NMS version string.
 *
 * @param version the CraftBukkit NMS version string
 * @param separator the package name part separator
 * @return the string with the replacement
 */
fun String.replaceCraftBukkitNMSVersion(version: String?, separator: Char = '/'): String =
    version?.let { replace("${separator}VVV${separator}", "${separator}$it${separator}") } ?: this

/**
 * Replaces `VVV` in Spigot mappings for the appropriate CraftBukkit NMS version string.
 *
 * This method expects [AbstractSpigotMappingResolver.META_CB_NMS_VERSION] metadata to be present.
 *
 * @param namespace the namespace, whose mappings are to be modified
 */
fun MappingTree.replaceCraftBukkitNMSVersion(namespace: String) {
    val namespaceId = getNamespaceId(namespace)
    require(namespaceId != MappingTree.NULL_NAMESPACE_ID) {
        "Namespace is not present in the mapping tree"
    }

    val nmsVersion = requireNotNull(craftBukkitNmsVersion) {
        "cb_nms_version metadata is not present"
    }

    classes.forEach { klass ->
        val original = klass.getDstName(namespaceId) ?: return@forEach
        val replaced = original.replaceCraftBukkitNMSVersion(nmsVersion)

        if (original != replaced) {
            klass.setDstName(replaced, namespaceId)
        }
    }
}
