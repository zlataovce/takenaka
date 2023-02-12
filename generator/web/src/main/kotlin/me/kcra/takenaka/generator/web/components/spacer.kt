package me.kcra.takenaka.generator.web.components

import kotlinx.html.*

/**
 * Appends a spacer component with a top margin.
 */
fun FlowContent.spacerTopComponent() {
    p(classes = "spacer-top")
}

/**
 * Appends a spacer component with a bottom margin.
 */
fun FlowContent.spacerBottomComponent() {
    p(classes = "spacer-bottom")
}
