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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.readText
import java.net.HttpURLConnection
import java.net.URL

/**
 * An internal instance of an [ObjectMapper].
 */
private val MAPPER = jacksonObjectMapper()

/**
 * A no-op implementation of a search index.
 */
private val DUMMY_IMPL = object : ClassSearchIndex {
    override val baseUrl: String
        get() = throw UnsupportedOperationException("baseUrl is not supported in the dummy impl")
    override fun linkClass(internalName: String): String? = null
}

/**
 * The base URL of the Java 21 JDK documentation.
 */
const val JDK_21_BASE_URL = "https://docs.oracle.com/en/java/javase/21/docs/api"

/**
 * Fetches and deserializes the package index on the base URL.
 *
 * @param baseUrl the base URL of the Javadoc site
 * @return the index
 */
fun modularClassSearchIndexOf(baseUrl: String): ModularClassSearchIndex {
    val content = URL("$baseUrl/package-search-index.js").httpRequest(action = HttpURLConnection::readText)
    val nodeArray = content.substring(
        content.indexOf('['),
        content.lastIndexOf(']') + 1
    )

    return ModularClassSearchIndex(baseUrl, MAPPER.readValue(nodeArray))
}

/**
 * Creates a class indexer for multiple Javadoc sites.
 *
 * @param indexes the indexers
 * @return the index
 */
fun compositeClassSearchIndexOf(vararg indexes: ClassSearchIndex) = CompositeClassSearchIndex(indexes.toList())

/**
 * Creates a class indexer for a pre-modular Javadoc site.
 *
 * @param baseUrl the base URL of the Javadoc site
 * @param supportedPackage the package that this indexer supports
 * @return the index
 */
fun classSearchIndexOf(baseUrl: String, supportedPackage: String) =
    PreModularClassSearchIndex(baseUrl, supportedPackage.toInternalName())

/**
 * Returns an empty class search index.
 *
 * @return an empty class search index
 */
fun emptyClassSearchIndex(): ClassSearchIndex = DUMMY_IMPL

/**
 * An index of a Javadoc site.
 *
 * @author Matouš Kučera
 */
interface ClassSearchIndex {
    /**
     * The base URL of the Javadoc site.
     */
    val baseUrl: String

    /**
     * Tries to make a URL to a foreign class.
     *
     * @param internalName the classes' internal name
     * @return the URL or null, if it's not in this index
     */
    fun linkClass(internalName: String): String?
}

/**
 * A Javadoc indexer for multiple sub-indexers.
 *
 * @property indexes the sub-indexers
 * @author Matouš Kučera
 */
class CompositeClassSearchIndex(val indexes: List<ClassSearchIndex>) : ClassSearchIndex {
    override val baseUrl: String
        get() = throw UnsupportedOperationException("baseUrl is not supported in the composite impl")

    /**
     * Tries to make a URL to a foreign class.
     *
     * @param internalName the classes' internal name
     * @return the URL or null, if it's not in this index
     */
    override fun linkClass(internalName: String): String? =
        indexes.firstNotNullOfOrNull { it.linkClass(internalName) }
}

/**
 * A module-to-package mapping of a Javadoc site.
 *
 * @property baseUrl the base URL of the Javadoc site
 * @property nodes the package nodes
 * @author Matouš Kučera
 */
class ModularClassSearchIndex(override val baseUrl: String, val nodes: List<Node>) : ClassSearchIndex {
    /**
     * A package-keyed index of [nodes].
     */
    private val packageIndex = nodes.associateBy(keySelector = Node::`package`)

    /**
     * Tries to make a URL to a foreign class.
     *
     * @param internalName the classes' internal name
     * @return the URL or null, if it's not in this index
     */
    override fun linkClass(internalName: String): String? {
        val klassPackage = internalName.fromInternalName().substringBeforeLast('.')
        if (klassPackage == internalName) return null

        val packageNode = packageIndex[klassPackage] ?: return null
        return "$baseUrl/${packageNode.module}/${internalName.replace('$', '.')}.html"
    }

    /**
     * A module and package pair from the package index.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Node(
        @JsonProperty("m")
        val module: String?,
        @JsonProperty("l")
        val `package`: String
    )
}

/**
 * A Javadoc indexer for the pre-modular era (Java 8 and lower).
 *
 * @property baseUrl the base URL of the Javadoc site
 * @property supportedPackage the package that this indexer supports
 * @author Matouš Kučera
 */
class PreModularClassSearchIndex(override val baseUrl: String, val supportedPackage: String) : ClassSearchIndex {
    /**
     * Tries to make a URL to a foreign class.
     *
     * @param internalName the classes' internal name
     * @return the URL or null, if it's not in this index
     */
    override fun linkClass(internalName: String): String? {
        if (internalName.startsWith(supportedPackage)) {
            return "$baseUrl/${internalName.replace('$', '.')}.html"
        }
        return null
    }
}
