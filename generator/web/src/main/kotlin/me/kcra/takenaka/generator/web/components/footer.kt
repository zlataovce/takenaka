package me.kcra.takenaka.generator.web.components

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.footer
import kotlinx.html.p

/**
 * Appends a footer.
 */
fun FlowContent.footerComponent() {
    footer {
        spacerBottomComponent()
        p {
            +"Generated with "
            a(href = "https://github.com/zlataovce/takenaka") {
                +"takenaka"
            }
        }
    }
}

/**
 * Appends a footer component placeholder that is replaced with a real navbar dynamically.
 */
fun FlowContent.footerPlaceholderComponent() {
    footer {}
}
