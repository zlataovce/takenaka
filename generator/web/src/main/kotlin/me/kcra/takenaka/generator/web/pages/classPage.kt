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
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.*
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.signature.SignatureReader
import org.w3c.dom.Document
import java.lang.reflect.Modifier

/**
 * Generates a class overview page.
 *
 * @param klass the class
 * @param workspace the workspace
 * @param friendlyNameRemapper the remapper for remapping signatures
 * @return the generated document
 */
fun GenerationContext.classPage(klass: MappingTree.ClassMapping, workspace: VersionedWorkspace, friendlyNameRemapper: Remapper): Document = createHTMLDocument().html {
    val friendlyName = getFriendlyDstName(klass).fromInternalName()

    headComponent(friendlyName)
    body {
        navPlaceholderComponent()
        main {
            val friendlyPackageName = friendlyName.substringBeforeLast('.')
            a(href = "/${workspace.version.id}/${friendlyPackageName.replace('.', '/')}/index.html") {
                +friendlyPackageName
            }

            val mod = klass.modifiers
            val signature = klass.signature

            var classHeader = formatClassHeader(friendlyName, mod)
            var classDescription = formatClassDescription(klass, mod, workspace.version, friendlyNameRemapper)
            if (signature != null) {
                val visitor = SignatureFormatter(klass.tree, index, friendlyNameRemapper, null, workspace.version, mod)
                SignatureReader(signature).accept(visitor)

                classHeader += visitor.formals
                classDescription = visitor.superTypes
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
                        val nsFriendlyName = generator.namespaceFriendlyNames[ns] ?: return@forEach

                        val name = klass.getName(id) ?: return@forEach
                        tr {
                            badgeColumnComponent(nsFriendlyName, generator.namespaceBadgeColors[ns] ?: "#94a3b8", styleSupplier)
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
                            val fieldMod = field.modifiers
                            if (generator.skipSynthetics && (fieldMod and Opcodes.ACC_SYNTHETIC) != 0) return@forEach

                            tr {
                                td(classes = "member-modifiers") {
                                    +formatModifiers(fieldMod, Modifier.fieldModifiers())

                                    unsafe {
                                        +formatType(Type.getType(field.srcDesc), workspace.version, friendlyNameRemapper)
                                    }
                                }
                                td {
                                    table {
                                        tbody {
                                            (MappingTree.SRC_NAMESPACE_ID until klass.tree.maxNamespaceId).forEach { id ->
                                                val ns = klass.tree.getNamespaceName(id)
                                                val nsFriendlyName = generator.namespaceFriendlyNames[ns]

                                                if (nsFriendlyName != null) {
                                                    val name = field.getName(id)
                                                    if (name != null) {
                                                        tr {
                                                            badgeColumnComponent(nsFriendlyName, generator.namespaceBadgeColors[ns] ?: "#94a3b8", styleSupplier)
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

                            val methodMod = method.modifiers
                            if (generator.skipSynthetics && (methodMod and Opcodes.ACC_SYNTHETIC) != 0) return@forEach

                            tr {
                                td(classes = "member-modifiers") {
                                    +formatModifiers(methodMod, Modifier.constructorModifiers())
                                }
                                td {
                                    p {
                                        unsafe {
                                            +formatMethodDescriptor(method, methodMod, workspace.version, friendlyNameRemapper, linkRemapper = null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (klass.methods.any { it.srcName != "<init>" && it.srcName != "<clinit>" }) {
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

                            val methodMod = method.modifiers
                            if (generator.skipSynthetics && (methodMod and Opcodes.ACC_SYNTHETIC) != 0) return@forEach

                            tr {
                                td(classes = "member-modifiers") {
                                    unsafe {
                                        var mask = Modifier.methodModifiers()
                                        // remove public modifiers on interface members, they are implicit
                                        if ((mod and Opcodes.ACC_INTERFACE) != 0) {
                                            mask = mask and Modifier.PUBLIC.inv()
                                        }

                                        +formatModifiers(methodMod, mask)

                                        val methodSignature = method.signature
                                        if (methodSignature != null) {
                                            val visitor = SignatureFormatter(method.tree, index, friendlyNameRemapper, null, workspace.version, mod)
                                            SignatureReader(methodSignature).accept(visitor)

                                            val formals = visitor.declaration.substringBefore('(')

                                            if (formals.isNotEmpty()) {
                                                +"$formals "
                                            }
                                            +(visitor.returnType ?: "")
                                        } else {
                                            +formatType(Type.getType(method.srcDesc).returnType, workspace.version, friendlyNameRemapper)
                                        }
                                    }
                                }
                                td {
                                    table {
                                        tbody {
                                            (MappingTree.SRC_NAMESPACE_ID until method.tree.maxNamespaceId).forEach { id ->
                                                val ns = method.tree.getNamespaceName(id)
                                                val nsFriendlyName = generator.namespaceFriendlyNames[ns]

                                                if (nsFriendlyName != null) {
                                                    val name = method.getName(id)
                                                    if (name != null) {
                                                        tr {
                                                            badgeColumnComponent(nsFriendlyName, generator.namespaceBadgeColors[ns] ?: "#94a3b8", styleSupplier)
                                                            td {
                                                                p(classes = "mapping-value") {
                                                                    unsafe {
                                                                        val remapper = ElementRemapper(method.tree) { it.getName(id) }

                                                                        +"$name${formatMethodDescriptor(method, methodMod, workspace.version, remapper, linkRemapper = friendlyNameRemapper, skipFormals = true)}"
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
        footerPlaceholderComponent()
    }
}

/**
 * Formats a class mapping to a class header (e.g. "public class HelloWorld").
 *
 * @param friendlyName the class friendly name
 * @param mod the class modifiers
 * @return the class header
 */
fun GenerationContext.formatClassHeader(friendlyName: String, mod: Int): String = buildString {
    append(formatModifiers(mod, Modifier.classModifiers()))
    when {
        (mod and Opcodes.ACC_ANNOTATION) != 0 -> append("@interface ") // annotations are interfaces, so this must be before ACC_INTERFACE
        (mod and Opcodes.ACC_INTERFACE) != 0 -> append("interface ")
        (mod and Opcodes.ACC_ENUM) != 0 -> append("enum ")
        (mod and Opcodes.ACC_MODULE) != 0 -> append("module ")
        (mod and Opcodes.ACC_RECORD) != 0 -> append("record ")
        else -> append("class ")
    }
    append(friendlyName.substringAfterLast('.'))
}

/**
 * Formats a class mapping to a class description (e.g. "implements net.minecraft.protocol.Packet").
 *
 * @param klass the class mapping
 * @param mod the class modifiers
 * @param version the mapping's version
 * @param nameRemapper the remapper for remapping signatures
 * @return the class description
 */
fun GenerationContext.formatClassDescription(klass: MappingTree.ClassMapping, mod: Int, version: Version, nameRemapper: Remapper): String = buildString {
    val superClass = klass.superClass
    val interfaces = klass.interfaces.filter { it != "java/lang/annotation/Annotation" }

    if (superClass != "java/lang/Object" && superClass != "java/lang/Record") {
        append("extends ${nameRemapper.mapTypeAndLink(version, superClass, index)}")
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
        append(" ${interfaces.joinToString(", ") { nameRemapper.mapTypeAndLink(version, it, index) }}")
    }
}

/**
 * Formats a method descriptor (e.g. "(String arg0, Throwable arg1)").
 *
 * @param method the method mapping
 * @param mod the method modifiers
 * @param version the mapping's version
 * @param nameRemapper the remapper for remapping signatures
 * @param linkRemapper the remapper used for remapping link addresses
 * @return the formatted descriptor
 */
fun GenerationContext.formatMethodDescriptor(method: MappingTree.MethodMapping, mod: Int, version: Version, nameRemapper: Remapper, linkRemapper: Remapper? = null, skipFormals: Boolean = false): String = buildString {
    // example:
    // descriptor: ([Ldyl;Ljava/util/Map;Z)V
    // signature: ([Ldyl;Ljava/util/Map<Lchq;Ldzg;>;Z)V
    // visited signature: (net.minecraft.world.level.storage.loot.predicates.LootItemCondition[], java.util.Map<net.minecraft.world.item.enchantment.Enchantment, net.minecraft.world.level.storage.loot.providers.number.NumberProvider>, boolean)void

    val signature = method.signature
    if (signature != null) {
        val visitor = SignatureFormatter(method.tree, index, nameRemapper, linkRemapper, version, mod)
        SignatureReader(signature).accept(visitor)

        val formals = visitor.declaration.substringBefore('(')
        if (!skipFormals && formals.isNotEmpty()) {
            append(formals).append(' ')
        }

        append('(')

        val args = visitor.declaration.substring(visitor.declaration.indexOf('(') + 1, visitor.declaration.lastIndexOf(')'))
        if (args.isNotEmpty()) {
            val splitArgs = args.split('+')

            var argumentIndex = 0
            append(
                splitArgs.joinToString { arg ->
                    val i = argumentIndex++
                    // if it's the last argument and the method has a variadic parameter, show it as such
                    return@joinToString if (i == (splitArgs.size - 1) && (mod and Opcodes.ACC_VARARGS) != 0 && arg.endsWith("[]")) {
                        "${arg.removeSuffix("[]")}... arg$i"
                    } else {
                        "$arg arg$i"
                    }
                }
            )
        }

        append(')')

        if (visitor.exceptions != null) {
            append(" throws ").append(visitor.exceptions)
        }

        return@buildString
    }

    // there's no generic signature, so just format the descriptor

    append('(')

    val args = Type.getType(method.srcDesc).argumentTypes
    var argumentIndex = 0
    append(
        args.joinToString { arg ->
            val i = argumentIndex++
            return@joinToString "${formatType(arg, version, nameRemapper, linkRemapper, isVarargs = i == (args.size - 1) && (mod and Opcodes.ACC_VARARGS) != 0)} arg$i"
        }
    )
    append(')')
}

/**
 * Formats a type with links and remaps any class names in it.
 *
 * @param type the type
 * @param version the version of the mappings
 * @param nameRemapper the name remapper
 * @param linkRemapper the link remapper, the remapped name will be used if it's null
 * @param isVarargs whether this is the last parameter of a method and the last array dimension should be made into a variadic parameter
 * @return the formatted type
 */
fun GenerationContext.formatType(type: Type, version: Version, nameRemapper: Remapper, linkRemapper: Remapper? = null, isVarargs: Boolean = false): String {
    return when (type.sort) {
        Type.ARRAY -> buildString {
            append(nameRemapper.mapTypeAndLink(version, type.elementType.className.toInternalName(), index, linkRemapper))
            var arrayDimensions = "[]".repeat(type.dimensions)
            if (isVarargs) {
                arrayDimensions =  "${arrayDimensions.substringBeforeLast("[]")}..."
            }
            append(arrayDimensions)
        }

        // Type#INTERNAL, it's private, so we need to use the value directly
        Type.OBJECT, 12 -> nameRemapper.mapTypeAndLink(version, type.internalName, index, linkRemapper)
        else -> type.className
    }
}
