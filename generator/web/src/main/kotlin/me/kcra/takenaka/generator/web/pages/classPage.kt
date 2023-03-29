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
import me.kcra.takenaka.core.mapping.allNamespaceIds
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.matchers.isConstructor
import me.kcra.takenaka.core.mapping.matchers.isEnumValueOf
import me.kcra.takenaka.core.mapping.matchers.isEnumValues
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
 * @param hash the history file hash
 * @param workspace the workspace
 * @param friendlyNameRemapper the remapper for remapping signatures
 * @return the generated document
 */
fun GenerationContext.classPage(klass: MappingTree.ClassMapping, hash: String?, workspace: VersionedWorkspace, friendlyNameRemapper: ElementRemapper): Document = createHTMLDocument().html {
    val klassDeclaration = formatClassDescriptor(klass, workspace.version, friendlyNameRemapper)

    headComponent("${workspace.version.id} - ${klassDeclaration.friendlyName}", workspace.version.id)
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
                    if (hash != null) {
                        +"""<a href="/history/$hash.html"><svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></a>"""
                    }
                }
            }
            if (klassDeclaration.superTypes != null) {
                p(classes = "class-description") {
                    unsafe {
                        +klassDeclaration.superTypes
                    }
                }
            }
            spacerTopComponent()
            table {
                tbody {
                    klass.tree.allNamespaceIds.forEach { id ->
                        val ns = klass.tree.getNamespaceName(id)
                        val namespace = generator.namespaces[ns] ?: return@forEach

                        val name = klass.getName(id) ?: return@forEach
                        tr {
                            badgeColumnComponent(namespace.friendlyName, namespace.color, styleConsumer)
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
                table(classes = "member-table") {
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
                                            +signature.formatTypeSignature(DefaultFormattingOptions.ESCAPE_HTML_SYMBOLS, friendlyNameRemapper, null, index, workspace.version).declaration
                                        } else {
                                            +formatType(Type.getType(field.srcDesc), workspace.version, friendlyNameRemapper)
                                        }
                                    }
                                }
                                td {
                                    table {
                                        tbody {
                                            klass.tree.allNamespaceIds.forEach { id ->
                                                val ns = klass.tree.getNamespaceName(id)
                                                val namespace = generator.namespaces[ns]

                                                if (namespace != null) {
                                                    val name = field.getName(id)
                                                    if (name != null) {
                                                        tr {
                                                            badgeColumnComponent(namespace.friendlyName, namespace.color, styleConsumer)
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
                table(classes = "member-table") {
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
                            if (!method.isConstructor) return@forEach

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
            if (klass.methods.any { !it.isConstructor }) { // static initializers are filtered in AbstractGenerator, no need to check it here
                spacerBottomComponent()
                h4 {
                    +"Method summary"
                }
                table(classes = "member-table") {
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
                            if (method.isConstructor) return@forEach
                            // skip implicit enum methods
                            if ((klassDeclaration.modifiers and Opcodes.ACC_ENUM) != 0 && (method.isEnumValueOf || method.isEnumValues)) return@forEach

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
                                            method.tree.allNamespaceIds.forEach { id ->
                                                val ns = method.tree.getNamespaceName(id)
                                                val namespace = generator.namespaces[ns]

                                                if (namespace != null) {
                                                    val methodName = method.getName(id)
                                                    if (methodName != null) {
                                                        tr {
                                                            badgeColumnComponent(namespace.friendlyName, namespace.color, styleConsumer)
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
 * @property superTypes the superclass and superinterfaces, including `extends` and `implements`, implicit ones are omitted (null if there are no non-implicit supertypes)
 */
data class ClassDeclaration(
    val friendlyName: String,
    val modifiers: Int,
    val modifiersAndName: String,
    val formals: String?,
    val superTypes: String?
)

/**
 * Formats a class descriptor and its generic signature.
 *
 * @param klass the class mapping
 * @param version the mapping's version
 * @param nameRemapper the remapper for remapping signatures
 * @return the formatted descriptor
 */
fun GenerationContext.formatClassDescriptor(klass: MappingTree.ClassMapping, version: Version, nameRemapper: ElementRemapper): ClassDeclaration {
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
        val options = buildFormattingOptions {
            escapeHtmlSymbols()

            if ((mod and Opcodes.ACC_INTERFACE) != 0) {
                interfaceSignature()
            }
        }

        val formatter = signature.formatSignature(options, remapper = nameRemapper, packageIndex = index, version = version)
        formals = formatter.formals
        superTypes = formatter.superTypes

        // remove the java/lang/Enum generic superclass, this is a hack and should have been done in SignatureFormatter,
        // however you would have to skip the superclass and then the following type parameter, so I think that this is the simpler way
        if ((mod and Opcodes.ACC_ENUM) != 0) {
            val implementsClauseIndex = superTypes.indexOf("implements")

            superTypes = if (implementsClauseIndex != -1) superTypes.substring(implementsClauseIndex) else ""
        }
    } else {
        val superClass = klass.superClass
        val interfaces = klass.interfaces.filter { it != "java/lang/annotation/Annotation" }

        superTypes = buildString {
            if (superClass != "java/lang/Object" && superClass != "java/lang/Record" && superClass != "java/lang/Enum") {
                append("extends ${nameRemapper.mapAndLink(superClass, version, index)}")
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
                append(" ${interfaces.joinToString(", ") { nameRemapper.mapAndLink(it, version, index) }}")
            }
        }
    }

    return ClassDeclaration(
        friendlyName,
        mod,
        modifiersAndName,
        formals,
        superTypes.ifBlank { null }
    )
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
fun GenerationContext.formatMethodDescriptor(method: MappingTree.MethodMapping, mod: Int, version: Version, nameRemapper: ElementRemapper, linkRemapper: Remapper? = null): MethodDeclaration {
    // example:
    // descriptor: ([Ldyl;Ljava/util/Map;Z)V
    // signature: ([Ldyl;Ljava/util/Map<Lchq;Ldzg;>;Z)V
    // visited signature: (net.minecraft.world.level.storage.loot.predicates.LootItemCondition[], java.util.Map<net.minecraft.world.item.enchantment.Enchantment, net.minecraft.world.level.storage.loot.providers.number.NumberProvider>, boolean)void

    val signature = method.signature
    if (signature != null) {
        val options = buildFormattingOptions {
            escapeHtmlSymbols()
            generateNamedParameters()
            adjustForMethod(mod)
        }

        val formatter = signature.formatSignature(options, method, nameRemapper, linkRemapper, index, version)
        return MethodDeclaration(
            formatter.formals.ifBlank { null },
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
                return@joinToString buildString {
                    append(formatType(arg, version, nameRemapper, linkRemapper, isVarargs = i == (args.size - 1) && (mod and Opcodes.ACC_VARARGS) != 0))

                    append(' ')

                    var lvIndex = i  // local variable index
                    // the first variable is the class instance if it's not static, so offset it
                    if ((mod and Opcodes.ACC_STATIC) == 0) lvIndex++

                    append(
                        method.getArg(-1, lvIndex, null)
                            ?.let(nameRemapper.elementMapper)
                            ?: "arg$i"
                    )
                }
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
fun GenerationContext.formatType(type: Type, version: Version, nameRemapper: ElementRemapper, linkRemapper: Remapper? = null, isVarargs: Boolean = false): String {
    return when (type.sort) {
        Type.ARRAY -> buildString {
            append(nameRemapper.mapAndLink(type.elementType.className.toInternalName(), version, index, linkRemapper))

            var arrayDimensions = type.dimensions
            if (isVarargs) arrayDimensions--

            append("[]".repeat(arrayDimensions))
            if (isVarargs) append("...")
        }

        // Type#INTERNAL, it's private, so we need to use the value directly
        Type.OBJECT, 12 -> nameRemapper.mapAndLink(type.internalName, version, index, linkRemapper)
        else -> type.className
    }
}
