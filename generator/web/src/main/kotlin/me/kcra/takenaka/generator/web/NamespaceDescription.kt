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
    val license: LicenseReference? = null
) {
    /**
     * @param friendlyName the namespace friendly name, which will be shown on the site
     * @param color the namespace badge color
     * @param licenseContentKey the license content metadata key for this namespace version
     * @param licenseSourceKey the license source metadata key for this namespace version
     */
    constructor(friendlyName: String, color: String, licenseContentKey: String, licenseSourceKey: String = "${licenseContentKey}_source") :
            this(friendlyName, color, LicenseReference(licenseContentKey, licenseSourceKey))
}
