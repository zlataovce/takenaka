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

package me.kcra.takenaka.generator.web.util

/**
 * A `<` (less than) character encoded for HTML usage.
 */
const val HTML_LT = "&lt;"

/**
 * A `>` (greater than) character encoded for HTML usage.
 */
const val HTML_GT = "&gt;"

/**
 * Escapes `<` and `>` characters in a string for HTML usage.
 *
 * @return the escaped string
 */
fun String.escapeHtml(): String = replace("<", HTML_LT).replace(">", HTML_GT)

/**
 * A `"` (double quotation mark) character encoded for HTML usage.
 */
const val HTML_QUOT = "&quot;"

/**
 * Escapes the `"` character in a string for usage in an HTML tag attribute value.
 *
 * @return the escaped string
 */
fun String.escapeHtmlAttribute(): String = replace("\"", HTML_QUOT)

/**
 * Void element tag names.
 *
 * See [the HTML specification](https://html.spec.whatwg.org/multipage/syntax.html#void-elements).
 */
val VOID_ELEMENTS = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")

/**
 * A HTML builder DSL.
 */
typealias HTMLBuilder = StringBuilder

/**
 * An HTML builder action.
 */
typealias HTMLBuilderBlock = HTMLBuilder.() -> Unit

/**
 * Builds an HTML document.
 *
 * @param docType whether a DOCTYPE should be prepended
 * @param block the builder action
 * @return the built document
 */
inline fun buildHTML(docType: Boolean = true, block: HTMLBuilderBlock): String = buildString {
    if (docType) {
        append("<!DOCTYPE html>")
    }

    block(this)
}

/**
 * Appends a tag.
 *
 * @param name the tag name
 * @param attrs the tag attributes
 * @param block the builder action
 */
inline fun HTMLBuilder.tag(
    name: String,
    attrs: Map<String, Any?> = emptyMap(),
    block: HTMLBuilderBlock = {},
) {
    append("<$name")
    attrs.forEach { (key, value) ->
        if (value == null || value == false) return@forEach

        append(" $key") // https://developer.mozilla.org/en-US/docs/Glossary/Boolean/HTML
        if (value !is Boolean) {
            append("=\"${value.toString().escapeHtmlAttribute()}\"")
        }
    }

    if (name in VOID_ELEMENTS) {
        append(" />")
    } else {
        append(">")

        block(this)
        append("</$name>")
    }
}

/**
 * Appends a `<html>` tag.
 *
 * @param lang the `lang` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.html(lang: String? = null, block: HTMLBuilderBlock = {}) =
    tag("html", mapOf("lang" to lang), block)

/**
 * Appends a `<head>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.head(block: HTMLBuilderBlock = {}) = tag("head", block = block)

/**
 * Appends a `<body>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.body(block: HTMLBuilderBlock = {}) = tag("body", block = block)

/**
 * Appends a `<title>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.title(block: HTMLBuilderBlock = {}) = tag("title", block = block)

/**
 * Appends a `<main>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.main(block: HTMLBuilderBlock = {}) = tag("main", block = block)

/**
 * Appends a `<h1>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.h1(block: HTMLBuilderBlock = {}) = tag("h1", block = block)

/**
 * Appends a `<h3>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.h3(block: HTMLBuilderBlock = {}) = tag("h3", block = block)

/**
 * Appends a `<h4>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.h4(block: HTMLBuilderBlock = {}) = tag("h4", block = block)

/**
 * Appends a `<p>` tag.
 *
 * @param onClick the `onclick` attribute
 * @param classes the `class` attribute
 * @param style the `style` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.p(onClick: String? = null, classes: String? = null, style: String? = null, block: HTMLBuilderBlock = {}) =
    tag("p", mapOf("onclick" to onClick, "class" to classes, "style" to style), block)

/**
 * Appends a `<pre>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.pre(block: HTMLBuilderBlock = {}) = tag("pre", block = block)

/**
 * Appends a `<table>` tag.
 *
 * @param classes the `class` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.table(classes: String? = null, block: HTMLBuilderBlock = {}) =
    tag("table", mapOf("class" to classes), block)

/**
 * Appends a `<thead>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.thead(block: HTMLBuilderBlock = {}) = tag("thead", block = block)

/**
 * Appends a `<tbody>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.tbody(block: HTMLBuilderBlock = {}) = tag("tbody", block = block)

/**
 * Appends a `<tr>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.tr(block: HTMLBuilderBlock = {}) = tag("tr", block = block)

/**
 * Appends a `<th>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.th(block: HTMLBuilderBlock = {}) = tag("th", block = block)

/**
 * Appends a `<td>` tag.
 *
 * @param classes the `class` attribute
 * @param style the `style` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.td(classes: String? = null, style: String? = null, block: HTMLBuilderBlock = {}) =
    tag("td", mapOf("class" to classes, "style" to style), block)

/**
 * Appends a `<div>` tag.
 *
 * @param id the `id` attribute
 * @param onClick the `onclick` attribute
 * @param classes the `class` attribute
 * @param style the `style` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.div(id: String? = null, onClick: String? = null, classes: String? = null, style: String? = null, block: HTMLBuilderBlock = {}) =
    tag("div", mapOf("id" to id, "onclick" to onClick, "class" to classes, "style" to style), block)

/**
 * Appends a `<span>` tag.
 *
 * @param classes the `class` attribute
 * @param style the `style` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.span(classes: String? = null, style: String? = null, block: HTMLBuilderBlock = {}) =
    tag("span", mapOf("class" to classes, "style" to style), block)

/**
 * Appends a `<a>` tag.
 *
 * @param id the `id` attribute
 * @param href the `href` attribute
 * @param classes the `class` attribute
 * @param ariaLabel the `aria-label` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.a(id: String? = null, href: String? = null, classes: String? = null, ariaLabel: String? = null, block: HTMLBuilderBlock = {}) =
    tag("a", mapOf("id" to id, "href" to href, "class" to classes, "aria-label" to ariaLabel), block)

/**
 * Appends a `<input>` tag.
 *
 * @param id the `id` attribute
 * @param type the `type` attribute
 * @param classes the `class` attribute
 * @param spellCheck the `spellcheck` attribute
 * @param placeholder the `placeholder` attribute
 */
fun HTMLBuilder.input(id: String? = null, type: String? = null, classes: String? = null, spellCheck: String? = null, placeholder: String? = null) =
    tag("input", mapOf("id" to id, "type" to type, "class" to classes, "spellcheck" to spellCheck, "placeholder" to placeholder))

/**
 * Appends a `<nav>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.nav(block: HTMLBuilderBlock = {}) = tag("nav", block = block)

/**
 * Appends a `<footer>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.footer(block: HTMLBuilderBlock = {}) = tag("footer", block = block)

/**
 * Appends a `<meta>` tag.
 *
 * @param name the `name` attribute
 * @param content the `content` attribute
 */
fun HTMLBuilder.meta(name: String? = null, content: String? = null) =
    tag("meta", mapOf("name" to name, "content" to content))

/**
 * Appends a `<script>` tag.
 *
 * @param src the `src` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.script(src: String? = null, block: HTMLBuilderBlock = {}) =
    tag("script", mapOf("src" to src), block)

/**
 * Appends a `<link>` tag.
 *
 * @param href the `href` attribute
 * @param rel the `rel` attribute
 */
fun HTMLBuilder.link(href: String? = null, rel: String? = null) =
    tag("link", mapOf("href" to href, "rel" to rel))
