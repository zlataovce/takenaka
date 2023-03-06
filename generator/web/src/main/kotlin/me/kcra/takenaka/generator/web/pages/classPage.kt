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
import me.kcra.takenaka.core.mapping.resolve.interfaces
import me.kcra.takenaka.core.mapping.resolve.modifiers
import me.kcra.takenaka.core.mapping.resolve.signature
import me.kcra.takenaka.core.mapping.resolve.superClass
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
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
    val klassDeclaration = formatClassDescriptor(klass, workspace.version, friendlyNameRemapper)

    headComponent(klassDeclaration.friendlyName, workspace.version.id)
    body {
        navPlaceholderComponent()
        main {
            val friendlyPackageName = klassDeclaration.friendlyName.substringBeforeLast('.')
            a(href = "/${workspace.version.id}/${friendlyPackageName.toInternalName()}/index.html") {
                +friendlyPackageName
            }

            p(classes = "class-header") {
                unsafe {
                    +klassDeclaration.modifiersAndName
                    klassDeclaration.formals?.unaryPlus()
                }
            }
            p(classes = "class-description") {
                unsafe {
                    +klassDeclaration.superTypes
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
                            badgeColumnComponent(nsFriendlyName, getNamespaceBadgeColor(ns), styleSupplier)
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

                            tr {
                                td(classes = "member-modifiers") {
                                    +formatModifiers(fieldMod, Modifier.fieldModifiers())

                                    unsafe {
                                        val signature = field.signature
                                        if (signature != null) {
                                            +signature.formatTypeSignature(formattingOptionsOf(ESCAPE_HTML_SYMBOLS), friendlyNameRemapper, null, index, workspace.version).declaration
                                        } else {
                                            +formatType(Type.getType(field.srcDesc), workspace.version, friendlyNameRemapper)
                                        }
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
                                                            badgeColumnComponent(nsFriendlyName, getNamespaceBadgeColor(ns), styleSupplier)
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

                            tr {
                                td(classes = "member-modifiers") {
                                    +formatModifiers(methodMod, Modifier.constructorModifiers())
                                }
                                td {
                                    p {
                                        unsafe {
                                            val ctorDeclaration = formatMethodDescriptor(method, methodMod, workspace.version, friendlyNameRemapper, linkRemapper = null)

                                            ctorDeclaration.formals?.unaryPlus()
                                            +ctorDeclaration.args
                                            ctorDeclaration.exceptions?.let { +" throws $it" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (klass.methods.any { it.srcName != "<init>" }) {
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
                            // skip constructors
                            if (method.srcName == "<init>") return@forEach

                            val methodMod = method.modifiers

                            tr {
                                td(classes = "member-modifiers") {
                                    unsafe {
                                        var mask = Modifier.methodModifiers()
                                        // remove public and abstract modifiers on interface members, they are implicit
                                        if ((klassDeclaration.modifiers and Opcodes.ACC_INTERFACE) != 0) {
                                            mask = mask and Modifier.PUBLIC.inv() and Modifier.ABSTRACT.inv()
                                        }

                                        +formatModifiers(methodMod, mask)

                                        val methodDeclaration = formatMethodDescriptor(method, methodMod, workspace.version, friendlyNameRemapper)
                                        methodDeclaration.formals?.let { +"$it " }
                                        +methodDeclaration.returnType
                                    }
                                }
                                td {
                                    table {
                                        tbody {
                                            (MappingTree.SRC_NAMESPACE_ID until method.tree.maxNamespaceId).forEach { id ->
                                                val ns = method.tree.getNamespaceName(id)
                                                val nsFriendlyName = generator.namespaceFriendlyNames[ns]

                                                if (nsFriendlyName != null) {
                                                    val methodName = method.getName(id)
                                                    if (methodName != null) {
                                                        tr {
                                                            badgeColumnComponent(nsFriendlyName, getNamespaceBadgeColor(ns), styleSupplier)
                                                            td {
                                                                p(classes = "mapping-value") {
                                                                    unsafe {
                                                                        val remapper = ElementRemapper(method.tree) { it.getName(id) }
                                                                        val methodDeclaration = formatMethodDescriptor(method, methodMod, workspace.version, remapper, linkRemapper = friendlyNameRemapper)

                                                                        +methodName
                                                                        +methodDeclaration.args
                                                                        methodDeclaration.exceptions?.let { +" throws $it" }
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
 * A formatted class descriptor.
 *
 * @property friendlyName the class's name, remapped as per the [formatClassDescriptor] `nameRemapper` parameter
 * @property modifiers the class's modifiers
 * @property modifiersAndName the stringified modifiers with the package-less class name
 * @property formals the formal generic type arguments of the class itself
 * @property superTypes the superclass and superinterfaces, including `extends` and `implements`, implicit ones are omitted
 */
data class ClassDeclaration(
    val friendlyName: String,
    val modifiers: Int,
    val modifiersAndName: String,
    val formals: String?,
    val superTypes: String
)

/**
 * Formats a class descriptor and its generic signature.
 *
 * @param klass the class mapping
 * @param version the mapping's version
 * @param nameRemapper the remapper for remapping signatures
 * @return the formatted descriptor
 */
fun GenerationContext.formatClassDescriptor(klass: MappingTree.ClassMapping, version: Version, nameRemapper: Remapper): ClassDeclaration {
    val friendlyName = getFriendlyDstName(klass).fromInternalName()
    val mod = klass.modifiers

    val modifiersAndName = buildString {
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

    var formals: String? = null
    lateinit var superTypes: String

    val signature = klass.signature
    if (signature != null) {
        var formattingOptions = formattingOptionsOf(ESCAPE_HTML_SYMBOLS)
        if ((mod and Opcodes.ACC_INTERFACE) != 0) formattingOptions = formattingOptions or INTERFACE_SIGNATURE

        val formatter = signature.formatSignature(formattingOptions, nameRemapper, null, index, version)
        formals = formatter.formals
        superTypes = formatter.superTypes
    } else {
        val superClass = klass.superClass
        val interfaces = klass.interfaces.filter { it != "java/lang/annotation/Annotation" }

        superTypes = buildString {
            if (superClass != "java/lang/Object" && superClass != "java/lang/Record" && superClass != "java/lang/Enum") {
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
    }

    return ClassDeclaration(friendlyName, mod, modifiersAndName, formals, superTypes)
}

/**
 * A formatted method descriptor.
 *
 * @property formals the formal generic type arguments of the method
 * @property args the method arguments, includes surrounding parentheses
 * @property returnType the method return type
 * @property exceptions the throws clause
 */
data class MethodDeclaration(
    val formals: String?,
    val args: String,
    val returnType: String,
    val exceptions: String?
)

/**
 * Formats a method descriptor and its generic signature.
 *
 * @param method the method mapping
 * @param mod the method modifiers
 * @param version the mapping's version
 * @param nameRemapper the remapper for remapping signatures
 * @param linkRemapper the remapper used for remapping link addresses
 * @return the formatted descriptor
 */
fun GenerationContext.formatMethodDescriptor(method: MappingTree.MethodMapping, mod: Int, version: Version, nameRemapper: Remapper, linkRemapper: Remapper? = null): MethodDeclaration {
    // example:
    // descriptor: ([Ldyl;Ljava/util/Map;Z)V
    // signature: ([Ldyl;Ljava/util/Map<Lchq;Ldzg;>;Z)V
    // visited signature: (net.minecraft.world.level.storage.loot.predicates.LootItemCondition[], java.util.Map<net.minecraft.world.item.enchantment.Enchantment, net.minecraft.world.level.storage.loot.providers.number.NumberProvider>, boolean)void

    val signature = method.signature
    if (signature != null) {
        var options = formattingOptionsOf(ESCAPE_HTML_SYMBOLS, GENERATE_NAMED_PARAMETERS)
        if ((mod and Opcodes.ACC_VARARGS) != 0) options = options or VARIADIC_PARAMETER

        val formatter = signature.formatSignature(options, nameRemapper, linkRemapper, index, version)
        return MethodDeclaration(
            formatter.formals.ifEmpty { null },
            formatter.args,
            formatter.returnType ?: error("Method signature without a return type"),
            formatter.exceptions
        )
    }

    // there's no generic signature, so just format the descriptor

    val type = Type.getType(method.srcDesc)
    val args = buildString {
        append('(')

        val args = type.argumentTypes
        var argumentIndex = 0
        append(
            args.joinToString { arg ->
                val i = argumentIndex++
                return@joinToString "${formatType(arg, version, nameRemapper, linkRemapper, isVarargs = i == (args.size - 1) && (mod and Opcodes.ACC_VARARGS) != 0)} arg$i"
            }
        )
        append(')')
    }

    return MethodDeclaration(
        null,
        args,
        formatType(type.returnType, version, nameRemapper, linkRemapper),
        null
    )
}

/**
 * Formats a **non-generic** type with links and remaps any class names in it.
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
                arrayDimensions =  "${arrayDimensions.drop(2)}..."
            }
            append(arrayDimensions)
        }

        // Type#INTERNAL, it's private, so we need to use the value directly
        Type.OBJECT, 12 -> nameRemapper.mapTypeAndLink(version, type.internalName, index, linkRemapper)
        else -> type.className
    }
}
