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

package me.kcra.takenaka.core.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path

/**
 * Creates a new ObjectMapper with all modules necessary for deserializing manifests.
 *
 * @return the object mapper
 */
fun objectMapper(): ObjectMapper = jsonMapper {
    addModule(kotlinModule())
    addModule(JavaTimeModule())
    enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
}

/**
 * Reads contents of a file.
 *
 * @param src the file
 */
inline fun <reified T> ObjectMapper.readValue(src: Path): T = readValue(src.toFile())

/**
 * Reads contents of a file into a tree.
 *
 * @param src the file
 * @return the tree
 */
fun ObjectMapper.readTree(src: Path): JsonNode = readTree(src.toFile())
