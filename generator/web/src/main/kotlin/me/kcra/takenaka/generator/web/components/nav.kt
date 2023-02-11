package me.kcra.takenaka.generator.web.components

import kotlinx.html.*

fun SectioningOrFlowContent.navComponent() {
    nav {
        div(classes = "nav-items") {
            p(classes = "nav-brand") {
                +"placeholder"
            }
            p {
                +"Overview"
            }
        }
        div(classes = "search") {
            input(type = InputType.text, classes = "search-input") {
                attributes["placeholder"] = "Type a package name..."
            }
            p(classes = "search-icon") {
                unsafe {
                    +"""
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
                    """.trimIndent()
                }
            }
        }
    }
}