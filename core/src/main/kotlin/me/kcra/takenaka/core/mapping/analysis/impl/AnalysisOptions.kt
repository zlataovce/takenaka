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

package me.kcra.takenaka.core.mapping.analysis.impl

import me.kcra.takenaka.core.mapping.resolve.impl.VanillaMappingContributor

/**
 * Basic analysis configuration.
 *
 * @property innerClassNameCompletionCandidates namespaces that should have inner class names completed (see [MappingAnalyzerImpl.acceptClass])
 * @property inheritanceErrorExemptions namespaces that shouldn't have inheritance errors/missing override mappings corrected/completed (see [MappingAnalyzerImpl.ClassContext.acceptMethod])
 * @property inheritanceAdditionalNamespaces destination namespaces that will be used for matching overridden methods in addition to the source (obfuscated) mapping
 * @property specialMethodExemptions namespaces that shouldn't have names of special methods completed/corrected
 */
data class AnalysisOptions(
    val innerClassNameCompletionCandidates: Set<String> = emptySet(),
    val inheritanceErrorExemptions: Set<String> = VanillaMappingContributor.NAMESPACES.toSet(),
    val inheritanceAdditionalNamespaces: Set<String> = emptySet(),
    val specialMethodExemptions: Set<String> = VanillaMappingContributor.NAMESPACES.toSet()
)