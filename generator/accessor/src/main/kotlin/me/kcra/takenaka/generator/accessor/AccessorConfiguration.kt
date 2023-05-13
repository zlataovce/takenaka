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

package me.kcra.takenaka.generator.accessor

import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import net.fabricmc.mappingio.MappingUtil

/**
 * Configuration for [AccessorGenerator].
 *
 * @property accessors the class accessor models
 * @property basePackage the base package of the generated accessors
 * @property languageFlavor the language of the generated code
 * @property namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @property accessedNamespaces the namespaces that should be used in accessors
 * @property craftBukkitVersionReplaceCandidates namespaces that should have [me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion] applied (most likely Spigot mappings or a flavor of them)
 * @property historicalNamespaces namespaces that should be used for computing history, empty if namespaces from [namespaceFriendlinessIndex] should be considered (excluding the obfuscated one)
 * @author Matouš Kučera
 */
data class AccessorConfiguration(
    val accessors: List<ClassAccessor>,
    val basePackage: String,
    val languageFlavor: LanguageFlavor = LanguageFlavor.JAVA,
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val accessedNamespaces: List<String> = namespaceFriendlinessIndex,
    val craftBukkitVersionReplaceCandidates: List<String> = emptyList(),
    val historicalNamespaces: List<String> = namespaceFriendlinessIndex - MappingUtil.NS_SOURCE_FALLBACK
)
