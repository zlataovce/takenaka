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

package me.kcra.takenaka.generator.accessor

import me.kcra.takenaka.generator.accessor.naming.NamingStrategy
import me.kcra.takenaka.generator.accessor.naming.StandardNamingStrategies

/**
 * The default package of the `generator-accessor-plugin` module.
 */
const val DEFAULT_RUNTIME_PACKAGE = "me.kcra.takenaka.accessor"

/**
 * Configuration for [AccessorGenerator].
 *
 * @property codeLanguage the language of the generated code
 * @property accessorType the form of generated accessors (not the mapping classes)
 * @property namespaceFriendlinessIndex an ordered list of namespaces that will be considered when selecting a "friendly" name
 * @property accessedNamespaces the namespaces that should be used in accessors
 * @property craftBukkitVersionReplaceCandidates namespaces that should have [me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion] applied (most likely Spigot mappings or a flavor of them)
 * @property namingStrategy the strategy used to name generated classes and their members
 * @property runtimePackage the package of the used accessor runtime
 * @property mappingWebsite the base url of the mapping website
 * @author Matouš Kučera
 */
data class AccessorConfiguration(
    val codeLanguage: CodeLanguage = CodeLanguage.JAVA,
    val accessorType: AccessorType = AccessorType.NONE,
    val namespaceFriendlinessIndex: List<String> = emptyList(),
    val accessedNamespaces: List<String> = namespaceFriendlinessIndex,
    val craftBukkitVersionReplaceCandidates: List<String> = emptyList(),
    val namingStrategy: NamingStrategy = StandardNamingStrategies.FULLY_QUALIFIED,
    val runtimePackage: String = DEFAULT_RUNTIME_PACKAGE,
    val mappingWebsite: String? = null
)
