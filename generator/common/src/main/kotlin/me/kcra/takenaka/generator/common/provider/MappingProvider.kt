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

package me.kcra.takenaka.generator.common.provider

import me.kcra.takenaka.core.mapping.MutableMappingsMap
import me.kcra.takenaka.core.mapping.analysis.MappingAnalyzer

/**
 * A provider of a set of mappings required for generation.
 *
 * @author Matouš Kučera
 */
interface MappingProvider {
    /**
     * Provides mappings and visits them to an analyzer.
     *
     * @param analyzer an analyzer which the mappings should be visited to as they are resolved
     * @return the mappings
     */
    suspend fun get(analyzer: MappingAnalyzer?): MutableMappingsMap

    /**
     * Provides mappings.
     *
     * @return the mappings
     */
    suspend fun get(): MutableMappingsMap = get(null)
}
