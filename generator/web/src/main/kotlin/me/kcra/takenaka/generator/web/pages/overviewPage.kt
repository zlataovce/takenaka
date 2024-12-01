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

package me.kcra.takenaka.generator.web.pages

import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.web.GenerationContext
import me.kcra.takenaka.generator.web.components.*
import me.kcra.takenaka.generator.web.util.*

/**
 * Generates an overview page.
 *
 * @param workspace the workspace
 * @param packages the packages in this version
 * @return the generated document
 */
fun GenerationContext.overviewPage(workspace: VersionedWorkspace, packages: Set<String>): String = buildHTML {
    html(lang = "en") {
        head {
            versionRootComponent()
            defaultResourcesComponent(rootPath = "../")
            if (generator.config.emitMetaTags) {
                metadataComponent(
                    title = workspace.version.id,
                    description = if (packages.size == 1) "1 package" else "${packages.size} packages",
                    themeColor = generator.config.themeColor
                )
            }
            title {
                append("overview - ${workspace.version.id}")
            }
        }
        body {
            navPlaceholderComponent()
            main {
                h1 {
                    append(workspace.version.id)
                }
                spacerBottomComponent()
                table(classes = "styled-table") {
                    thead {
                        tr {
                            th {
                                append("Package")
                            }
                        }
                    }
                    tbody {
                        packages.forEach { packageName ->
                            tr {
                                td {
                                    a(href = "$packageName/index.html") {
                                        append(packageName.fromInternalName())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            footerPlaceholderComponent()
        }
    }
}
