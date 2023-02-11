package me.kcra.takenaka.generator.web.components

import kotlinx.html.*

fun FlowContent.spacerTopComponent() {
    p(classes = "spacer-top")
}

fun FlowContent.spacerBottomComponent() {
    p(classes = "spacer-bottom")
}

fun TABLE.spacerTableComponent() {
    tr(classes = "table-spacer") {
        td {
            attributes["colspan"] = "100%"
        }
    }
    tr {
        style = "height:10px"
    }
}
