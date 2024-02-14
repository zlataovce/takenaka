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

package me.kcra.takenaka.generator.web

/**
 * A license metadata key pair.
 *
 * @property content the license content metadata key
 * @property source the license source metadata key
 */
data class LicenseReference(val content: String, val source: String)

/**
 * A license declaration.
 *
 * @property content the license content
 * @property source the license source, probably a link
 */
data class License(val content: String, val source: String)
