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

package me.kcra.takenaka.generator.web.components

import me.kcra.takenaka.generator.web.util.*

/**
 * Appends a spacer component with a top margin.
 */
fun HTMLBuilder.spacerTopComponent() {
    p(classes = "spacer-top")
}

/**
 * Appends a spacer component with a top margin, but without a bottom margin.
 */
fun HTMLBuilder.spacerTopSlimComponent() {
    p(classes = "spacer-top-slim")
}

/**
 * Appends a spacer component with a bottom margin.
 */
fun HTMLBuilder.spacerBottomComponent() {
    p(classes = "spacer-bottom")
}

/**
 * Appends a spacer component with a bottom margin, but without a top margin.
 */
fun HTMLBuilder.spacerBottomSlimComponent() {
    p(classes = "spacer-bottom-slim")
}

/**
 * Appends a spacer component with a top and bottom margin.
 */
fun HTMLBuilder.spacerYComponent() {
    p(classes = "spacer-y")
}
