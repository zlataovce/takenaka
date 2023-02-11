package me.kcra.takenaka.generator.web.components

import kotlinx.html.FlowContent
import kotlinx.html.p
import kotlinx.html.style

fun FlowContent.badgeComponent(content: String, color: String) {
    p(classes = "badge") {
        style = "background-color:$color"
        +content
    }
}
