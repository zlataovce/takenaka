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

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream
import kotlin.reflect.KProperty

/**
 * A delegate that provides a thread-local [MessageDigest].
 *
 * @property value the [ThreadLocal]
 */
class ThreadLocalMessageDigestDelegate(private val value: ThreadLocal<MessageDigest>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): MessageDigest = value.get()
}

/**
 * Creates a thread-local [MessageDigest] with syntax sugar.
 *
 * @param algorithm the digest algorithm
 */
fun threadLocalMessageDigest(algorithm: String) =
    ThreadLocalMessageDigestDelegate(ThreadLocal.withInitial { MessageDigest.getInstance(algorithm) })

/**
 * A thread-local SHA-1 digest.
 */
val sha1Digest by threadLocalMessageDigest("SHA-1")

/**
 * A thread-local MD5 digest.
 */
val md5Digest by threadLocalMessageDigest("MD5")

/**
 * Computes a checksum using the provided digest, outputting it in a hexadecimal format.
 *
 * @param digest the digest
 * @return the checksum
 */
fun Path.getChecksum(digest: MessageDigest): String {
    inputStream().use {
        val buffer = ByteArray(1024)
        var bytesCount: Int
        while (it.read(buffer).also { b -> bytesCount = b } != -1) {
            digest.update(buffer, 0, bytesCount)
        }
    }

    return digest.hexValue
}

/**
 * Returns the value of this digest in a hexadecimal format.
 */
val MessageDigest.hexValue: String
    get() {
        val bytes = digest()
        return buildString {
            for (i in bytes.indices) {
                append(((bytes[i].toInt() and 0xff) + 0x100).toString(16).substring(1))
            }
        }
    }

/**
 * Updates the digest using the specified string encoded to UTF-8.
 *
 * @param input the string
 */
fun MessageDigest.update(input: String) {
    update(input.encodeToByteArray())
}

/**
 * Updates the digest using the supplied bytes and returns the hexadecimal value.
 *
 * @param input the array of bytes
 */
fun MessageDigest.updateAndHex(input: ByteArray): String {
    update(input)
    return hexValue
}

/**
 * Updates the digest using the specified string encoded to UTF-8 and returns the hexadecimal value.
 *
 * @param input the string
 */
fun MessageDigest.updateAndHex(input: String): String {
    update(input)
    return hexValue
}
