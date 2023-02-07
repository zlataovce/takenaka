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

package me.kcra.takenaka.core.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Computes a checksum using the provided digest, outputting it in a hexadecimal format.
 *
 * @param digest the digest
 * @return the checksum
 */
fun File.getChecksum(digest: MessageDigest): String {
    FileInputStream(this).use {
        val buffer = ByteArray(1024)
        var bytesCount: Int
        while (it.read(buffer).also { b -> bytesCount = b } != -1) {
            digest.update(buffer, 0, bytesCount)
        }
    }

    val bytes = digest.digest()
    return buildString {
        for (i in bytes.indices) {
            append(((bytes[i].toInt() and 0xff) + 0x100).toString(16).substring(1))
        }
    }
}
