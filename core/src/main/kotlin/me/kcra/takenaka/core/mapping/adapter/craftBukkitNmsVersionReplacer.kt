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

import me.kcra.takenaka.core.mapping.resolve.AbstractSpigotMappingResolver
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTree

private val logger = KotlinLogging.logger {}

/**
 * Replaces `VVV` in Spigot mappings for the appropriate CraftBukkit NMS version string.
 *
 * This method expects [AbstractSpigotMappingResolver.META_CB_NMS_VERSION] metadata to be present.
 *
 * @param namespace the namespace, whose mappings are to be modified
 */
fun MappingTree.replaceCraftBukkitNMSVersion(namespace: String = "spigot") {
    val namespaceId = getNamespaceId(namespace)
    if (namespaceId == MappingTree.NULL_NAMESPACE_ID) {
        error("Namespace is not present in the mapping tree")
    }

    val nmsVersion = getMetadata(AbstractSpigotMappingResolver.META_CB_NMS_VERSION)
        ?: error("cb_nms_version metadata is not present")

    classes.forEach { klass ->
        val original = klass.getDstName(namespaceId) ?: return@forEach
        val replaced = original.replace("VVV", nmsVersion)

        logger.debug { "replaced $original -> $replaced" }
        klass.setDstName(replaced, namespaceId)
    }
}
