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

package me.kcra.takenaka.generator.common

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.kcra.takenaka.core.mapping.MutableMappingsMap
import me.kcra.takenaka.core.mapping.analysis.MappingAnalyzer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A class that provides a set of mappings required for generation.
 *
 * @author Matouš Kučera
 */
interface MappingProvider {
    /**
     * Provides the mappings.
     *
     * @param analyzer an analyzer which the mappings should be visited to as they are resolved
     * @return the mappings
     */
    suspend fun get(analyzer: MappingAnalyzer? = null): MutableMappingsMap
}

/**
 * Maps an [Iterable] in parallel.
 *
 * @param context the coroutine context
 * @param block the mapping function
 * @return the remapped list
 */
suspend fun <A, B> Iterable<A>.parallelMap(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend (A) -> B
): List<B> = coroutineScope {
    map { async(context) { block(it) } }.awaitAll()
}
