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

package me.kcra.takenaka.generator.common.provider.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import me.kcra.takenaka.core.mapping.MappingContributor
import net.fabricmc.mappingio.MappingVisitor

private val logger = KotlinLogging.logger {}

/**
 * A wrapping mapping contributor that retries for [retries] times, if an exception is thrown.
 *
 * @property contributor the wrapped contributor
 * @property retries the number of retries after which we should give up
 * @property name the contributor name (used for diagnostics)
 * @author Matouš Kučera
 */
internal class RetryingContributor(
    val contributor: MappingContributor,
    val retries: Int,
    val name: String = contributor.javaClass.simpleName
) : MappingContributor {
    override val targetNamespace by contributor::targetNamespace

    override fun accept(visitor: MappingVisitor) {
        repeat(retries - 1) { i ->
            try {
                return contributor.accept(visitor)
            } catch (e: Throwable) {
                logger.error(e) { "Mapping contributor '$name' threw exception, try ${i + 1}/$retries" }
            }
        }

        return contributor.accept(visitor)
    }
}
