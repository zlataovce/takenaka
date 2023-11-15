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

/**
 * Configuration for [AccessorGenerator].
 *
 * @property accessors the class accessor models
 * @property basePackage the base package name of the generated accessors
 * @property codeLanguage the language of the generated code
 * @property accessorType the form of generated accessors (not the mapping classes)
 * @property namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @property accessedNamespaces the namespaces that should be used in accessors
 * @property craftBukkitVersionReplaceCandidates namespaces that should have [me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion] applied (most likely Spigot mappings or a flavor of them)
 * @author Matouš Kučera
 */
data class AccessorConfiguration(
    val accessors: List<ClassAccessor>, // TODO: move to AccessorGenerator constructor
    val basePackage: String,
    val codeLanguage: CodeLanguage = CodeLanguage.JAVA,
    val accessorType: AccessorType = AccessorType.NONE,
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val accessedNamespaces: List<String> = namespaceFriendlinessIndex,
    val craftBukkitVersionReplaceCandidates: List<String> = emptyList()
)
