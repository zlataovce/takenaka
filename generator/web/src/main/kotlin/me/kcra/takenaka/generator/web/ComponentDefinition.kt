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

import kotlinx.html.BODY
import kotlinx.html.body
import kotlinx.html.consumers.filter
import kotlinx.html.dom.append
import kotlinx.html.dom.document
import kotlinx.html.html
import org.w3c.dom.Document
import kotlin.properties.Delegates

/**
 * A HTML component.
 *
 * @property tag the main tag of the component, any tag that is empty and has the same name, will be replaced with the component
 * @property content the component content/body
 * @property callback a raw JS script that is called after the component has been loaded onto the site, an `e` variable of type `HTMLElement` is available
 */
data class ComponentDefinition(
    val tag: String,
    val content: Document,
    val callback: String?
)

/**
 * An HTML component builder.
 *
 * @property tag the main tag of the component, any tag that is empty and has the same name, will be replaced with the component
 * @property content the component content/body
 * @property callback a raw JS script that is called after the component has been loaded onto the site, an `e` variable of type `HTMLElement` is available
 */
class ComponentDefinitionBuilder {
    var tag by Delegates.notNull<String>()
    var content by Delegates.notNull<Document>()
    var callback: String? = null

    /**
     * Builds and sets the content of this component.
     *
     * @param block the builder action
     */
    inline fun content(crossinline block: BODY.() -> Unit) {
        content = document {
            append.filter { if (it.tagName in listOf("html", "body")) SKIP else PASS }
                .html {
                    body {
                        block()
                    }
                }
        }
    }

    /**
     * Creates an immutable component out of this builder.
     *
     * @return the component
     */
    fun toComponent() = ComponentDefinition(tag, content, callback)
}

/**
 * Builds an HTML component.
 *
 * @param block the builder action
 * @return the component
 */
inline fun component(block: ComponentDefinitionBuilder.() -> Unit): ComponentDefinition = ComponentDefinitionBuilder().apply(block).toComponent()
