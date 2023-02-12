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
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.VersionedWorkspace
import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import me.kcra.takenaka.core.util.MappingTreeRemapper
import me.kcra.takenaka.generator.web.LinkingTraceSignatureVisitor
import me.kcra.takenaka.generator.web.WebGenerator
import me.kcra.takenaka.generator.web.components.*
import me.kcra.takenaka.generator.web.mapTypeAndLink
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.signature.SignatureReader
import org.w3c.dom.Document
import java.lang.reflect.Modifier

private val tracedSignatureRegex = "&lt;.+&gt;".toRegex()

/**
 * Generates a class overview page.
 *
 * @param workspace the workspace
 * @param friendlyNameRemapper the remapper for remapping signatures
 * @param klass the class
 * @return the generated document
 */
fun WebGenerator.classPage(workspace: VersionedWorkspace, friendlyNameRemapper: Remapper, klass: MappingTree.ClassMapping): Document = createHTMLDocument().html {
    val friendlyName = getFriendlyDstName(klass).fromInternalName()

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

            var classHeader = formatClassHeader(friendlyName, mod)
            var classDescription = formatClassDescription(klass, friendlyNameRemapper, workspace.version, mod)
            if (signature != null) {
                val visitor = LinkingTraceSignatureVisitor(klass.tree, friendlyNameRemapper, workspace.version, mod)
                SignatureReader(signature).accept(visitor)

                val declaration = visitor.declaration.trim()

                // FIXME: make a custom implementation, I was too lazy to reimplement TraceSignatureVisitor
                if (declaration.startsWith("&lt;")) {
                    val classTypeArgument = tracedSignatureRegex.find(declaration)?.value ?: ""

                    classHeader += classTypeArgument
                    classDescription = declaration.removePrefix(classTypeArgument).trimStart()
                } else if (declaration.startsWith("implements") || declaration.startsWith("extends")) {
                    classDescription = declaration
                }
            }

            p(classes = "class-header") {
                unsafe {
                    +classHeader
                }
            }
            p(classes = "class-description") {
                unsafe {
                    +classDescription
                }
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
                                    +name.fromInternalName()
                                }
                            }
                        }
                    }
                }
            }
            if (klass.fields.isNotEmpty()) {
                spacerBottomComponent()
                h4 {
                    +"Field summary"
                }
                table(classes = "member-table row-borders") {
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
                                td(classes = "member-modifiers") {
                                    +formatModifiers(fieldMod, Modifier.fieldModifiers())

                                    val type = Type.getType(field.srcDesc).className
                                    val fieldKlass = field.tree.getClass(type)
                                    if (fieldKlass != null) {
                                        val friendlyFieldKlass = getFriendlyDstName(fieldKlass)

                                        a(href = "/${workspace.version.id}/$friendlyFieldKlass.html") {
                                            +friendlyFieldKlass.substringAfterLast('/')
                                        }
                                    } else {
                                        +type
                                    }
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
            if (klass.methods.any { it.srcName == "<init>" }) {
                spacerBottomComponent()
                h4 {
                    +"Constructor summary"
                }
                table(classes = "member-table row-borders") {
                    thead {
                        tr {
                            th {
                                +"Modifier"
                            }
                            th {
                                +"Constructor"
                            }
                        }
                    }
                    tbody {
                        klass.methods.forEach { method ->
                            if (method.srcName != "<init>") return@forEach

                            val methodMod = method.getName(VanillaMappingContributor.NS_MODIFIERS).toInt()
                            tr {
                                td(classes = "member-modifiers") {
                                    +formatModifiers(methodMod, Modifier.constructorModifiers())
                                }
                                td {
                                    p {
                                        unsafe {
                                            +formatMethodDescriptor(method, friendlyNameRemapper, workspace.version, methodMod, skipReturnType = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (klass.methods.any { it.srcName == "<init>" }) {
                spacerBottomComponent()
                h4 {
                    +"Method summary"
                }
                table(classes = "member-table row-borders") {
                    thead {
                        tr {
                            th {
                                +"Modifier and Type"
                            }
                            th {
                                +"Method"
                            }
                        }
                    }
                    tbody {
                        klass.methods.forEach { method ->
                            // skip constructors and static initializers
                            if (method.srcName == "<init>" || method.srcName == "<clinit>") return@forEach

                            val methodMod = method.getName(VanillaMappingContributor.NS_MODIFIERS).toInt()
                            tr {
                                td(classes = "member-modifiers") {
                                    +formatModifiers(methodMod, Modifier.methodModifiers())

                                    val type = Type.getType(method.srcDesc).returnType.className
                                    val methodKlass = method.tree.getClass(type)
                                    if (methodKlass != null) {
                                        val friendlyMethodKlass = getFriendlyDstName(methodKlass)

                                        a(href = "/${workspace.version.id}/$friendlyMethodKlass.html") {
                                            +friendlyMethodKlass.substringAfterLast('/')
                                        }
                                    } else {
                                        +type
                                    }
                                }
                                td {
                                    table {
                                        tbody {
                                            (MappingTree.SRC_NAMESPACE_ID until method.tree.maxNamespaceId).forEach { id ->
                                                val ns = method.tree.getNamespaceName(id)
                                                val nsFriendlyName = namespaceFriendlyNames[ns]

                                                if (nsFriendlyName != null) {
                                                    val name = method.getName(id)
                                                    if (name != null) {
                                                        tr {
                                                            td {
                                                                badgeComponent(nsFriendlyName, namespaceBadgeColors[ns] ?: "#94a3b8")
                                                            }
                                                            td {
                                                                p(classes = "mapping-value") {
                                                                    unsafe {
                                                                        val remapper = MappingTreeRemapper(method.tree) { it.getName(id) }

                                                                        +"$name${formatMethodDescriptor(method, remapper, workspace.version, methodMod, skipReturnType = true)}"
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
    }
}

/**
 * Formats a class mapping to a class header (e.g. "public class HelloWorld").
 *
 * @param friendlyName the class friendly name
 * @param mod the class modifiers
 * @return the class header
 */
private fun formatClassHeader(friendlyName: String, mod: Int): String = buildString {
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
 * @param nameRemapper the remapper for remapping signatures
 * @param version the mapping's version
 * @param mod the class modifiers
 * @return the class description
 */
private fun formatClassDescription(klass: MappingTree.ClassMapping, nameRemapper: Remapper, version: Version, mod: Int): String = buildString {
    val superClass = klass.getName(VanillaMappingContributor.NS_SUPER) ?: "java/lang/Object"
    val interfaces = klass.getName(VanillaMappingContributor.NS_INTERFACES)?.split(',') ?: emptyList()

    if (superClass != "java/lang/Object") {
        append("extends ${nameRemapper.mapTypeAndLink(version, superClass)}")
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
        append(" ${interfaces.joinToString(", ") { nameRemapper.mapTypeAndLink(version, it) }}")
    }
}

/**
 * Formats a method descriptor (e.g. "(String arg0, Throwable arg1)").
 *
 * @param method the method mapping
 * @param nameRemapper the remapper for remapping signatures
 * @param version the mapping's version
 * @param mod the method modifiers
 * @param skipReturnType whether the return type should be skipped
 * @return the formatted descriptor
 */
private fun formatMethodDescriptor(method: MappingTree.MethodMapping, nameRemapper: Remapper, version: Version, mod: Int, skipReturnType: Boolean = false): String = buildString {
    // example:
    // descriptor: ([Ldyl;Ljava/util/Map;Z)V
    // signature: ([Ldyl;Ljava/util/Map<Lchq;Ldzg;>;Z)V
    // visited signature: (net.minecraft.world.level.storage.loot.predicates.LootItemCondition[], java.util.Map<net.minecraft.world.item.enchantment.Enchantment, net.minecraft.world.level.storage.loot.providers.number.NumberProvider>, boolean)void

    val signature = method.getName(VanillaMappingContributor.NS_SIGNATURE)
    if (signature != null) {
        val visitor = LinkingTraceSignatureVisitor(method.tree, nameRemapper, version, mod)
        SignatureReader(signature).accept(visitor)

        val returnType = visitor.declaration.substringAfterLast(')')
        if (!skipReturnType) {
            append(returnType).append(' ')
        }

        append('(')

        val args = visitor.declaration.removeSuffix(returnType).trim('(', ')').split(", ")
        var argumentIndex = 0
        append(
            args.joinToString { arg ->
                val i = argumentIndex++
                // if it's the last argument and the method has a variadic parameter, show it as such
                return@joinToString if (i == (args.size - 1) && (mod and Opcodes.ACC_VARARGS) != 0 && arg.endsWith("[]")) {
                    "${arg.removeSuffix("[]")}... arg$i"
                } else {
                    "$arg arg$i"
                }
            }
        )

        append(')')
        return@buildString
    }

    // there's no generic signature, so just format the descriptor

    val type = Type.getType(method.srcDesc)
    if (!skipReturnType) {
        append(nameRemapper.mapTypeAndLink(version, type.returnType.internalName)).append(' ')
    }

    append('(')

    val args = type.argumentTypes
    var argumentIndex = 0
    append(
        args.joinToString { arg ->
            val i = argumentIndex++
            return@joinToString when (arg.sort) {
                Type.ARRAY -> buildString {
                    append(nameRemapper.mapTypeAndLink(version, arg.elementType.internalName))
                    var arrayDimensions = "[]".repeat(arg.dimensions)
                    if ((mod and Opcodes.ACC_VARARGS) != 0) {
                        arrayDimensions =  "${arrayDimensions.substringBeforeLast("[]")}..."
                    }
                    append(arrayDimensions)
                }

                // Type#INTERNAL, it's private, so we need to use the value directly
                Type.OBJECT, 12 -> nameRemapper.mapTypeAndLink(version, arg.internalName)
                else -> arg.className
            } + " arg$i"
        }
    )
    append(')')
}
