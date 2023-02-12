package me.kcra.takenaka.generator.web.components

import kotlinx.html.FlowContent
import kotlinx.html.p
import kotlinx.html.style

/**
 * Appends a namespace badge component.
 *
 * @param content the namespace name
 * @param color the badge color in a CSS compatible format
 */
fun FlowContent.badgeComponent(content: String, color: String) {
    p(classes = "badge") {
        style = "background-color:$color"
        +content
    }
}
