package me.kcra.takenaka.generator.web.components

import kotlinx.html.*

/**
 * Appends a head component.
 */
fun HTML.headComponent(title: String) {
    head {
        link(href = "/assets/main.css", rel = "stylesheet")
        script(src = "/assets/main.js") {}
        script(src = "/assets/components.js") {}
        title(content = title)
    }
}
