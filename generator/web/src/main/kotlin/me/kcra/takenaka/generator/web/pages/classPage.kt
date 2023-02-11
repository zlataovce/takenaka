/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023 Matous Kucera
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

import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.generator.web.WebGenerator
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.util.TraceSignatureVisitor
import org.w3c.dom.Document
import java.lang.reflect.Modifier

/**
 * Generates a class overview page.
 *
 * @param workspace the workspace
 * @param klass the class
 * @return the generated document
 */
fun WebGenerator.classPage(workspace: VersionedWorkspace, klass: MappingTree.ClassMapping): Document = createHTMLDocument().html {
    val friendlyName = getFriendlyDstName(klass).replace('/', '.')

    head {
        link(href = "/assets/main.css", rel = "stylesheet")
        title(content = friendlyName)
    }
    body {
        navComponent()
        main {
            a(href = "#") {
                +friendlyName.substringBeforeLast('.')
            }

            val mod = klass.getName(VanillaMappingContributor.NS_MODIFIERS).toInt()
            val signature = klass.getName(VanillaMappingContributor.NS_SIGNATURE)

            var classHeader = formatClassHeader(klass, mod, friendlyName)
            var classDescription = formatClassDescription(klass, mod)
            if (signature != null) {
                val visitor = TraceSignatureVisitor(mod)
                SignatureReader(signature).accept(visitor)

                // FIXME: make a custom implementation, I was too lazy to reimplement TraceSignatureVisitor
                if (visitor.declaration.startsWith('<')) {
                    var openedTypeArguments = 0
                    val classTypeArgument = visitor.declaration.takeWhile { c ->
                        when (c) {
                            '<' -> openedTypeArguments++
                            '>' -> openedTypeArguments--
                        }

                        return@takeWhile openedTypeArguments > 0
                    }

                    classHeader += visitor.declaration.substring(0, classTypeArgument.length + 1)
                    classDescription = visitor.declaration.substring(classTypeArgument.length + 1).trimStart()
                }
            }

            p(classes = "class-header") {
                +classHeader
            }
            p(classes = "class-description") {
                +classDescription
            }
            spacerTopComponent()
            table {
                tbody {
                    (MappingTree.SRC_NAMESPACE_ID until klass.tree.maxNamespaceId).forEach { id ->
                        val ns = klass.tree.getNamespaceName(id)
                        val nsFriendlyName = namespaceFriendlyNames[ns] ?: return@forEach

                        val name = klass.getName(id) ?: return@forEach
                        tr {
                            td {
                                badgeComponent(nsFriendlyName, namespaceBadgeColors[ns] ?: "#94a3b8")
                            }
                            td {
                                p(classes = "mapping-value") {
                                    +name
                                }
                            }
                        }
                    }
                }
            }
            spacerBottomComponent()
            table(classes = "member-table") {
                spacerTableComponent()
                thead {
                    tr {
                        th {
                            +"Modifier and Type"
                        }
                        th {
                            +"Field"
                        }
                    }
                }
                tbody {
                    klass.fields.forEach { field ->
                        val fieldMod = field.getName(VanillaMappingContributor.NS_MODIFIERS).toInt()

                        tr {
                            td(classes = "modifiers") {
                                +formatModifiers(fieldMod, Modifier.fieldModifiers())
                            }
                            td {
                                table {
                                    tbody {
                                        (MappingTree.SRC_NAMESPACE_ID until klass.tree.maxNamespaceId).forEach { id ->
                                            val ns = klass.tree.getNamespaceName(id)
                                            val nsFriendlyName = namespaceFriendlyNames[ns]

                                            if (nsFriendlyName != null) {
                                                val name = field.getName(id)
                                                if (name != null) {
                                                    tr {
                                                        td {
                                                            badgeComponent(nsFriendlyName, namespaceBadgeColors[ns] ?: "#94a3b8")
                                                        }
                                                        td {
                                                            p(classes = "mapping-value") {
                                                                +name
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a class mapping to a class header (e.g. "public class HelloWorld").
 *
 * @param klass the class mapping
 * @param mod the class modifiers
 * @param friendlyName the class friendly name
 * @return the class header
 */
private fun formatClassHeader(klass: MappingTree.ClassMapping, mod: Int, friendlyName: String): String = buildString {
    append(formatModifiers(mod, Modifier.classModifiers()))
    when {
        (mod and Opcodes.ACC_INTERFACE) != 0 -> append("interface ")
        (mod and Opcodes.ACC_ANNOTATION) != 0 -> append("@interface ")
        (mod and Opcodes.ACC_ENUM) != 0 -> append("enum ")
        (mod and Opcodes.ACC_MODULE) != 0 -> append("module ")
        (mod and Opcodes.ACC_RECORD) != 0 -> append("record ")
        else -> append("class ")
    }
    append(friendlyName.substringAfterLast('.'))
}

/**
 * Formats a class mapping to a class description (e.g. "extends Object implements net.minecraft.protocol.Packet").
 *
 * @param klass the class mapping
 * @param mod the class modifiers
 * @return the class description
 */
private fun formatClassDescription(klass: MappingTree.ClassMapping, mod: Int): String = buildString {
    val superClass = klass.getName(VanillaMappingContributor.NS_SUPER) ?: "java/lang/Object"
    val interfaces = klass.getName(VanillaMappingContributor.NS_INTERFACES)?.split(',') ?: emptyList()

    if (superClass != "java/lang/Object") {
        append("extends ${superClass.replace('/', '.')}")
        if (interfaces.isNotEmpty()) {
            append(" ")
        }
    }
    if (interfaces.isNotEmpty()) {
        append(
            when {
                (mod and Opcodes.ACC_INTERFACE) != 0 -> "extends"
                else -> "implements"
            }
        )
        append(" ${interfaces.joinToString(", ") { it.replace('/', '.') }}")
    }
}
