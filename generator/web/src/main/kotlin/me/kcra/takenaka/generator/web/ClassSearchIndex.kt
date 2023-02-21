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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.util.httpRequest
import me.kcra.takenaka.core.util.readText
import java.net.HttpURLConnection
import java.net.URL

/**
 * A no-op implementation of a search index.
 */
private val DUMMY_IMPL = object : ClassSearchIndex {
    override val baseUrl: String
        get() = throw UnsupportedOperationException("baseUrl is not supported in the dummy impl")
    override fun linkClass(internalName: String): String? = null
}

/**
 * The base URL of the Java 17 JDK documentation.
 */
const val JDK_17_BASE_URL = "https://docs.oracle.com/en/java/javase/17/docs/api"

/**
 * Fetches and deserializes the package index on the base URL.
 *
 * @param baseUrl the base URL of the Javadoc site
 * @return the index
 */
fun ObjectMapper.modularClassSearchIndexOf(baseUrl: String): ModularClassSearchIndex {
    val content = URL("$baseUrl/package-search-index.js").httpRequest(action = HttpURLConnection::readText)
    val nodeArray = content.dropWhile { it != '[' }.dropLastWhile { it != ']' }

    return ModularClassSearchIndex(baseUrl, readValue(nodeArray))
}

/**
 * Creates a class indexer for multiple Javadoc sites.
 *
 * @param indexes the indexers
 * @return the index
 */
fun compositeClassSearchIndexOf(vararg indexes: ClassSearchIndex): CompositeClassSearchIndex =
    CompositeClassSearchIndex(indexes.toList())

/**
 * Creates a class indexer for a pre-modular Javadoc site.
 *
 * @param baseUrl the base URL of the Javadoc site
 * @param supportedPackage the package that this indexer supports
 * @return the index
 */
fun classSearchIndexOf(baseUrl: String, supportedPackage: String): PreModularClassSearchIndex =
    PreModularClassSearchIndex(baseUrl, supportedPackage.replace('.', '/'))

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
    override fun linkClass(internalName: String): String? {
        indexes.forEach { indexer ->
            return indexer.linkClass(internalName) ?: return@forEach
        }
        return null
    }
}

/**
 * A module-to-package mapping of a Javadoc site.
 *
 * @property baseUrl the base URL of the Javadoc site
 * @property index the package nodes
 * @author Matouš Kučera
 */
data class ModularClassSearchIndex(override val baseUrl: String, val index: List<Node>) : ClassSearchIndex {
    /**
     * Tries to make a URL to a foreign class.
     *
     * @param internalName the classes' internal name
     * @return the URL or null, if it's not in this index
     */
    override fun linkClass(internalName: String): String? {
        val klassPackage = internalName.fromInternalName().substringBeforeLast('.')
        if (klassPackage == internalName) return null

        val packageNode = index.find { it.`package` == klassPackage } ?: return null
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
data class PreModularClassSearchIndex(override val baseUrl: String, val supportedPackage: String) : ClassSearchIndex {
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
