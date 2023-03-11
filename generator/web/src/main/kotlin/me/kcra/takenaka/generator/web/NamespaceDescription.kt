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

package me.kcra.takenaka.generator.web

/**
 * A description of a namespace.
 *
 * @property friendlyName the namespace friendly name, which will be shown on the site
 * @property color the namespace badge color
 * @property license the license metadata keys for this namespace version
 */
data class NamespaceDescription(
    val friendlyName: String,
    val color: String,
    val license: LicenseReference?
)

/**
 * Creates a new namespace description.
 *
 * @param friendlyName the namespace friendly name, which will be shown on the site
 * @param color the namespace badge color
 * @return the namespace description
 */
fun namespaceDescOf(friendlyName: String, color: String) = NamespaceDescription(friendlyName, color, null)

/**
 * Creates a new namespace description.
 *
 * @param friendlyName the namespace friendly name, which will be shown on the site
 * @param color the namespace badge color
 * @param licenseContent the license content metadata key
 * @param licenseSource the license source metadata key
 * @return the namespace description
 */
fun namespaceDescOf(
    friendlyName: String,
    color: String,
    licenseContent: String,
    licenseSource: String = "${licenseContent}_source"
) = NamespaceDescription(friendlyName, color, licenseReferenceOf(licenseContent, licenseSource))
