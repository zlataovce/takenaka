package me.kcra.takenaka.generator.web.util

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
 * Builds an HTML document.
 *
 * @param docType whether a DOCTYPE should be prepended
 * @param block the builder action
 * @return the built document
 */
inline fun buildHTML(docType: Boolean = true, block: HTMLBuilder.() -> Unit): String {
    return buildString {
        if (docType) {
            append("<!DOCTYPE html>")
        }

        block(this)
    }
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
    block: HTMLBuilder.() -> Unit = {},
) {
    append("<${name}")
    attrs.forEach { (key, value) ->
        if (value != null) {
            append(" ${key}=\"${value}\"")
        }
    }

    if (name in VOID_ELEMENTS) {
        append(" />")
    } else {
        append(">")

        block(this)
        append("</${name}>")
    }
}

/**
 * Appends a `<html>` tag.
 *
 * @param lang the `lang` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.html(lang: String? = null, block: HTMLBuilder.() -> Unit = {}) =
    tag("html", mapOf("lang" to lang), block)

/**
 * Appends a `<head>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.head(block: HTMLBuilder.() -> Unit = {}) = tag("head", block = block)

/**
 * Appends a `<body>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.body(block: HTMLBuilder.() -> Unit = {}) = tag("body", block = block)

/**
 * Appends a `<title>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.title(block: HTMLBuilder.() -> Unit = {}) = tag("title", block = block)

/**
 * Appends a `<main>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.main(block: HTMLBuilder.() -> Unit = {}) = tag("main", block = block)

/**
 * Appends a `<h1>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.h1(block: HTMLBuilder.() -> Unit = {}) = tag("h1", block = block)

/**
 * Appends a `<h3>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.h3(block: HTMLBuilder.() -> Unit = {}) = tag("h3", block = block)

/**
 * Appends a `<h4>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.h4(block: HTMLBuilder.() -> Unit = {}) = tag("h4", block = block)

/**
 * Appends a `<p>` tag.
 *
 * @param onClick the `onclick` attribute
 * @param classes the `class` attribute
 * @param style the `style` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.p(onClick: String? = null, classes: String? = null, style: String? = null, block: HTMLBuilder.() -> Unit = {}) =
    tag("p", mapOf("onclick" to onClick, "class" to classes, "style" to style), block)

/**
 * Appends a `<pre>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.pre(block: HTMLBuilder.() -> Unit = {}) = tag("pre", block = block)

/**
 * Appends a `<table>` tag.
 *
 * @param classes the `class` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.table(classes: String? = null, block: HTMLBuilder.() -> Unit = {}) =
    tag("table", mapOf("class" to classes), block)

/**
 * Appends a `<thead>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.thead(block: HTMLBuilder.() -> Unit = {}) = tag("thead", block = block)

/**
 * Appends a `<tbody>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.tbody(block: HTMLBuilder.() -> Unit = {}) = tag("tbody", block = block)

/**
 * Appends a `<tr>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.tr(block: HTMLBuilder.() -> Unit = {}) = tag("tr", block = block)

/**
 * Appends a `<th>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.th(block: HTMLBuilder.() -> Unit = {}) = tag("th", block = block)

/**
 * Appends a `<td>` tag.
 *
 * @param classes the `class` attribute
 * @param style the `style` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.td(classes: String? = null, style: String? = null, block: HTMLBuilder.() -> Unit = {}) =
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
inline fun HTMLBuilder.div(id: String? = null, onClick: String? = null, classes: String? = null, style: String? = null, block: HTMLBuilder.() -> Unit = {}) =
    tag("div", mapOf("id" to id, "onclick" to onClick, "class" to classes, "style" to style), block)

/**
 * Appends a `<span>` tag.
 *
 * @param classes the `class` attribute
 * @param style the `style` attribute
 * @param block the builder action
 */
inline fun HTMLBuilder.span(classes: String? = null, style: String? = null, block: HTMLBuilder.() -> Unit = {}) =
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
inline fun HTMLBuilder.a(id: String? = null, href: String? = null, classes: String? = null, ariaLabel: String? = null, block: HTMLBuilder.() -> Unit = {}) =
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
fun HTMLBuilder.input(id: String? = null, type: String? = null, classes: String? = null, spellCheck: Boolean? = null, placeholder: String? = null) =
    tag("input", mapOf("id" to id, "type" to type, "class" to classes, "spellcheck" to spellCheck, "placeholder" to placeholder))

/**
 * Appends a `<nav>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.nav(block: HTMLBuilder.() -> Unit = {}) = tag("nav", block = block)

/**
 * Appends a `<footer>` tag.
 *
 * @param block the builder action
 */
inline fun HTMLBuilder.footer(block: HTMLBuilder.() -> Unit = {}) = tag("footer", block = block)

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
inline fun HTMLBuilder.script(src: String? = null, block: HTMLBuilder.() -> Unit = {}) =
    tag("script", mapOf("src" to src), block)

/**
 * Appends a `<link>` tag.
 *
 * @param href the `href` attribute
 * @param rel the `rel` attribute
 */
fun HTMLBuilder.link(href: String? = null, rel: String? = null) =
    tag("link", mapOf("href" to href, "rel" to rel))
