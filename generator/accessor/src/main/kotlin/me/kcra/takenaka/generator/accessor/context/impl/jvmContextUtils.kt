/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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

package me.kcra.takenaka.generator.accessor.context.impl

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.ElementMappingView

/**
 * Common function for reconstructing the member chain and finding the lowest/highest versions of each item. Used by [JavaGenerationContext] and [KotlinGenerationContext].
 *
 * @param memberAccessor member accessor
 * @return a triple consisting of all chained items (including their lowest/highest versions), lowest version and highest version
 */
fun <M, T: MappingTreeView, E: ElementMappingView> resolveMemberChain(memberAccessor: ResolvedMemberAccessor<M, T, E>): Triple<List<VersionedAccessor<M, T, E>>, Version, Version> {
    val chain = memberAccessor.map { (member, ancestryNode) -> VersionedAccessor(member, ancestryNode) }

    val lowestVersion = chain.minOf { it.lowestVersion }
    val highestVersion = chain.maxOf { it.highestVersion }

    return Triple(chain, lowestVersion, highestVersion)
}

data class VersionedAccessor<M, T: MappingTreeView, E: ElementMappingView>(
    val memberAccessor: M,
    val ancestryNode: AncestryTree.Node<T, E>,
    val lowestVersion: Version = ancestryNode.first.key,
    val highestVersion: Version = ancestryNode.last.key
)