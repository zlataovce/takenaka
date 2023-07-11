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

package me.kcra.takenaka.generator.common.provider.impl

import me.kcra.takenaka.core.mapping.MutableMappingsMap
import me.kcra.takenaka.core.mapping.analysis.MappingAnalyzer
import me.kcra.takenaka.generator.common.provider.MappingProvider

/**
 * A [MappingProvider] implementation that provides a pre-defined set of mappings.
 *
 * @property mappings the mappings
 */
class SimpleMappingProvider(val mappings: MutableMappingsMap) : MappingProvider {
    /**
     * Provides mappings and visits them to an analyzer.
     *
     * @param analyzer an analyzer which the mappings should be visited to as they are resolved
     * @return the mappings
     */
    override suspend fun get(analyzer: MappingAnalyzer?): MutableMappingsMap {
        if (analyzer != null) {
            mappings.values.forEach(analyzer::accept)
        }

        return mappings
    }
}