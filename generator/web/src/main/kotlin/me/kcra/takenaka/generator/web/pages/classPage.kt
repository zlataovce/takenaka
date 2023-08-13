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
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.core.mapping.analysis.impl.InheritanceWalkMode
import me.kcra.takenaka.core.mapping.analysis.impl.resolveSuperTypes
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.matchers.isConstructor
import me.kcra.takenaka.core.mapping.matchers.isEnumValueOf
import me.kcra.takenaka.core.mapping.matchers.isEnumValues
import me.kcra.takenaka.core.mapping.resolve.impl.interfaces
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.core.mapping.resolve.impl.signature
import me.kcra.takenaka.core.mapping.resolve.impl.superClass
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.core.mapping.util.allNamespaceIds
import me.kcra.takenaka.core.mapping.util.formatModifiers
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.components.*
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.w3c.dom.Document
import java.lang.reflect.Modifier

/**
 * Generates a class overview page.
 *
 * @param klass the class
 * @param hash the history file hash
 * @param nmsVersion the CraftBukkit NMS version string
 * @param workspace the workspace
 * @param friendlyNameRemapper the remapper for remapping signatures
 * @return the generated document
 */
fun GenerationContext.classPage(klass: MappingTreeView.ClassMappingView, hash: String?, nmsVersion: String?, workspace: VersionedWorkspace, friendlyNameRemapper: ElementRemapper): Document = createHTMLDocument().html {
    val klassName = getFriendlyDstName(klass)
    val friendlyKlassName = klassName.fromInternalName()

    val versionRootPath = getClassRelativeVersionRoot(klassName)
    val rootPath = "../$versionRootPath"

    val remapper = ContextualElementRemapper(friendlyNameRemapper, null, generator.config.index) { name ->
        getClassRelativePath(klassName, name)
    }
    val klassDeclaration = klass.formatDescriptor(friendlyKlassName, remapper)
    head {
        versionRootComponent(rootPath = versionRootPath)
        defaultResourcesComponent(rootPath)
        if (generator.config.emitMetaTags) {
            metadataComponent(
                title = friendlyKlassName,
                description = buildString {
                    append("version: ${workspace.version.id}")
                    if (hash != null) {
                        append(", hash: $hash")
                    }
                },
                themeColor = generator.config.themeColor
            )
        }
        title(content = "${workspace.version.id} - $friendlyKlassName")
    }
    body {
        navPlaceholderComponent()
        main {
            a(href = "index.html") {
                +friendlyKlassName.substringBeforeLast('.')
            }

            div(classes = "class-header") {
                p {
                    unsafe {
                        +klassDeclaration.modifiersAndName
                        klassDeclaration.formals?.unaryPlus()
                    }
                }
                if (hash != null) {
                    a(classes = "history-icon", href = "${rootPath}history/$hash.html") {
                        unsafe {
                            +"""<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>"""
                        }
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

            if (klassDeclaration.hasVisibleSuperClass) { // don't show superinterfaces when there's no actual superclass
                val superInterfaces = klass.resolveSuperTypes(InheritanceWalkMode.INTERFACES)
                if (superInterfaces.isNotEmpty()) {
                    p(classes = "interfaces-header") {
                        +"All mapped superinterfaces:"
                    }
                    p(classes = "interfaces-description") {
                        superInterfaces.forEachIndexed { i, s ->
                            val friendlyName = getFriendlyDstName(s)

                            a(href = getClassRelativePath(klassName, friendlyName)) {
                                +friendlyName.substringAfterLast('/')
                            }
                            if (i != superInterfaces.lastIndex) +", "
                        }
                    }
                }
            }

            spacerTopComponent()
            table {
                tbody {
                    klass.tree.allNamespaceIds.forEach { id ->
                        val ns = klass.tree.getNamespaceName(id)
                        val namespace = generator.config.namespaces[ns] ?: return@forEach

                        val namespacedNmsVersion = if (ns in versionReplaceCandidates) nmsVersion else null
                        val name = klass.getName(id)?.replaceCraftBukkitNMSVersion(namespacedNmsVersion) ?: return@forEach
                        tr {
                            badgeColumnComponent(namespace.friendlyName, namespace.color, styleProvider)
                            td(classes = "mapping-value") {
                                +name.fromInternalName()
                            }
                        }
                    }
                }
            }

            var nextSpacerSlim = false
            fun addContentSpacer() {
                if (nextSpacerSlim) {
                    spacerBottomSlimComponent()
                } else {
                    spacerBottomComponent()
                    nextSpacerSlim = true
                }
            }

            if (klass.fields.isNotEmpty()) {
                var fieldMask = Modifier.fieldModifiers()
                // remove public, static and final modifiers on interface fields, implicit
                // see: https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.3
                if ((klassDeclaration.modifiers and Opcodes.ACC_INTERFACE) != 0) {
                    fieldMask = fieldMask and Modifier.PUBLIC.inv() and Modifier.STATIC.inv() and Modifier.FINAL.inv()
                }

                var fields = klass.fields

                if (classTypeOf(klassDeclaration.modifiers) == ClassType.ENUM) {
                    val enumModifier = Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL or Opcodes.ACC_ENUM

                    val (enumFields, nonEnumFields) = fields.partition { (it.modifiers and enumModifier) == enumModifier && it.srcDesc == 'L' + klass.srcName + ';' }

                    if (enumFields.isNotEmpty()) {
                        fields = nonEnumFields

                        addContentSpacer()
                        h4 {
                            +"Enum constant summary"
                        }
                        table(classes = "styled-table styled-mobile-table") {
                            thead {
                                tr {
                                    th {
                                        +"Enum Constant"
                                    }
                                }
                            }
                            tbody {
                                enumFields.forEach { field ->
                                    tr {
                                        td {
                                            table {
                                                tbody {
                                                    klass.tree.allNamespaceIds.forEach { id ->
                                                        val ns = klass.tree.getNamespaceName(id)
                                                        val namespace = generator.config.namespaces[ns]

                                                        if (namespace != null) {
                                                            val name = field.getName(id)
                                                            if (name != null) {
                                                                tr {
                                                                    badgeColumnComponent(namespace.friendlyName, namespace.color, styleProvider)
                                                                    td(classes = "mapping-value") {
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

                if (fields.isNotEmpty()) {
                    addContentSpacer()
                    h4 {
                        +"Field summary"
                    }
                    table(classes = "styled-table styled-mobile-table") {
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
                            fields.forEach { field ->
                                tr {
                                    td(classes = "modifier-value") {
                                        +field.modifiers.formatModifiers(fieldMask)

                                        unsafe {
                                            +field.formatDescriptor(remapper)
                                        }
                                    }
                                    td {
                                        table {
                                            tbody {
                                                klass.tree.allNamespaceIds.forEach { id ->
                                                    val ns = klass.tree.getNamespaceName(id)
                                                    val namespace = generator.config.namespaces[ns]

                                                    if (namespace != null) {
                                                        val name = field.getName(id)
                                                        if (name != null) {
                                                            tr {
                                                                badgeColumnComponent(namespace.friendlyName, namespace.color, styleProvider)
                                                                td(classes = "mapping-value") {
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

            val constructors = klass.methods.filter(MappingTreeView.MethodMappingView::isConstructor)
            if (constructors.isNotEmpty()) {
                addContentSpacer()
                h4 {
                    +"Constructor summary"
                }
                table(classes = "styled-table") {
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
                        constructors.forEach { ctor ->
                            val ctorMod = ctor.modifiers
                            tr {
                                td(classes = "modifier-value") {
                                    +ctorMod.formatModifiers(Modifier.constructorModifiers())
                                }
                                td(classes = "constructor-value") {
                                    unsafe {
                                        val ctorDeclaration = ctor.formatDescriptor(remapper, ctorMod)

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

            fun ContextualElementRemapper.reset() {
                nameRemapper = friendlyNameRemapper
                linkRemapper = null
            }

            // skip constructors and implicit enum methods
            val methods = klass.methods.filter { !it.isConstructor && ((klassDeclaration.modifiers and Opcodes.ACC_ENUM) == 0 || !(it.isEnumValueOf || it.isEnumValues)) }
            if (methods.isNotEmpty()) {
                val methodMask = Modifier.methodModifiers()
                val interfaceMethod = (klassDeclaration.modifiers and Opcodes.ACC_INTERFACE) != 0

                addContentSpacer()
                h4 {
                    +"Method summary"
                }
                table(classes = "styled-table styled-mobile-table") {
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
                        methods.forEach { method ->
                            val methodMod = method.modifiers
                            tr {
                                td(classes = "modifier-value") {
                                    unsafe {
                                        +methodMod.formatModifiers(methodMask, interfaceMethod)

                                        val methodDeclaration = method.formatDescriptor(remapper, methodMod)
                                        methodDeclaration.formals?.let { +"$it " }
                                        +methodDeclaration.returnType
                                    }
                                }
                                td {
                                    table {
                                        tbody {
                                            method.tree.allNamespaceIds.forEach { id ->
                                                val ns = method.tree.getNamespaceName(id)
                                                val namespace = generator.config.namespaces[ns]

                                                val namespacedNmsVersion = if (ns in versionReplaceCandidates) nmsVersion else null
                                                if (namespace != null) {
                                                    val methodName = method.getName(id)
                                                    if (methodName != null) {
                                                        tr {
                                                            badgeColumnComponent(namespace.friendlyName, namespace.color, styleProvider)
                                                            td(classes = "mapping-value") {
                                                                unsafe {
                                                                    remapper.nameRemapper = ElementRemapper(method.tree) { it.getName(id)?.replaceCraftBukkitNMSVersion(namespacedNmsVersion) }
                                                                    remapper.linkRemapper = friendlyNameRemapper

                                                                    val methodDeclaration = method.formatDescriptor(remapper, methodMod)
                                                                    remapper.reset()

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
        footerPlaceholderComponent()
    }
}

/**
 * A formatted class descriptor.
 *
 * @property modifiers the class's modifiers
 * @property modifiersAndName the stringified modifiers with the package-less class name
 * @property formals the formal generic type arguments of the class itself
 * @property superTypes the superclass and superinterfaces, including `extends` and `implements`, implicit ones are omitted (null if there are no non-implicit supertypes)
 * @property hasVisibleSuperClass whether a superclass is visible in [superTypes]
 */
data class ClassDeclaration(
    val modifiers: Int,
    val modifiersAndName: String,
    val formals: String?,
    val superTypes: String?,
    val hasVisibleSuperClass: Boolean
)

/**
 * Formats a class descriptor and its generic signature.
 *
 * @param friendlyName the friendly name of the class
 * @param remapper the name remapper
 * @return the formatted descriptor
 */
fun <T : MappingTreeView.ClassMappingView> T.formatDescriptor(
    friendlyName: String,
    remapper: ContextualElementRemapper
): ClassDeclaration {
    val mod = this.modifiers
    val superClass = this.superClass
    val interfaces = this.interfaces

    val modifiersAndName = buildString {
        append(mod.formatModifiers(Modifier.classModifiers()))
        when {
            (mod and Opcodes.ACC_ANNOTATION) != 0 -> append("@interface ") // annotations are interfaces, so this must be before ACC_INTERFACE
            (mod and Opcodes.ACC_INTERFACE) != 0 -> {
                // no ACC_ANNOTATION, but looks like an annotation; must be an evil obfuscator
                if ("java/lang/annotation/Annotation" in interfaces) append('@')

                append("interface ")
            }
            (mod and Opcodes.ACC_ENUM) != 0 || superClass == "java/lang/Enum" -> append("enum ")
            (mod and Opcodes.ACC_MODULE) != 0 -> append("module ")
            (mod and Opcodes.ACC_RECORD) != 0 || superClass == "java/lang/Record" -> append("record ")
            else -> append("class ")
        }
        append(friendlyName.substringAfterLast('.'))
    }

    var formals: String? = null
    lateinit var superTypes: String

    val hasVisibleSuperClass = superClass != "java/lang/Object" && superClass != "java/lang/Record" && superClass != "java/lang/Enum"

    val signature = this.signature
    if (signature != null) {
        val options = buildFormattingOptions {
            escapeHtmlSymbols()

            if ((mod and Opcodes.ACC_INTERFACE) != 0) {
                interfaceSignature()
            }
        }

        val formatter = signature.formatSignature(options, remapper = remapper)
        formals = formatter.formals
        superTypes = formatter.superTypes

        // remove the java/lang/Enum generic superclass, this is a hack and should have been done in SignatureFormatter,
        // however you would have to skip the superclass and then the following type parameter, so I think that this is the simpler way
        if ((mod and Opcodes.ACC_ENUM) != 0 || superClass == "java/lang/Enum") {
            val implementsClauseIndex = superTypes.indexOf("implements")

            superTypes = if (implementsClauseIndex != -1) superTypes.substring(implementsClauseIndex) else ""
        }
    } else {
        val nonImplicitInterfaces = interfaces.filter { it != "java/lang/annotation/Annotation" }

        superTypes = buildString {
            if (hasVisibleSuperClass) {
                append("extends ${remapper.mapAndLink(superClass)}")
                if (nonImplicitInterfaces.isNotEmpty()) {
                    append(" ")
                }
            }
            if (nonImplicitInterfaces.isNotEmpty()) {
                append(
                    when {
                        (mod and Opcodes.ACC_INTERFACE) != 0 -> "extends"
                        else -> "implements"
                    }
                )
                append(" ${nonImplicitInterfaces.joinToString(", ", transform = remapper::mapAndLink)}")
            }
        }
    }

    return ClassDeclaration(
        mod,
        modifiersAndName,
        formals,
        superTypes.ifBlank { null },
        hasVisibleSuperClass
    )
}

/**
 * Formats a field descriptor/generic signature into a textual representation, without modifiers.
 *
 * @param remapper the name remapper
 * @return the textual representation
 */
fun <T : MappingTreeView.FieldMappingView> T.formatDescriptor(remapper: ContextualElementRemapper): String {
    return this.signature?.formatTypeSignature(DefaultFormattingOptions.ESCAPE_HTML_SYMBOLS, remapper)?.declaration
        ?: Type.getType(this.srcDesc).format(remapper)
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
 * @param remapper the name remapper
 * @param mod the method modifiers
 * @param generateNamedParameters whether named (or generated) parameter names should be added
 * @return the formatted descriptor
 */
fun <T : MappingTreeView.MethodMappingView> T.formatDescriptor(
    remapper: ContextualElementRemapper,
    mod: Int = this.modifiers,
    generateNamedParameters: Boolean = true
): MethodDeclaration {
    // example:
    // descriptor: ([Ldyl;Ljava/util/Map;Z)V
    // signature: ([Ldyl;Ljava/util/Map<Lchq;Ldzg;>;Z)V
    // visited signature: (net.minecraft.world.level.storage.loot.predicates.LootItemCondition[], java.util.Map<net.minecraft.world.item.enchantment.Enchantment, net.minecraft.world.level.storage.loot.providers.number.NumberProvider>, boolean)void

    val signature = this.signature
    if (signature != null) {
        val options = buildFormattingOptions {
            escapeHtmlSymbols()
            if (generateNamedParameters) generateNamedParameters()
            adjustForMethod(mod)
        }

        val formatter = signature.formatSignature(options, this, remapper)
        return MethodDeclaration(
            formatter.formals.ifBlank { null },
            formatter.args,
            checkNotNull(formatter.returnType) { "Method signature without a return type" },
            formatter.exceptions
        )
    }

    // there's no generic signature, so just format the descriptor

    val type = Type.getType(this.srcDesc)
    val args = buildString {
        append('(')

        var argIndex = 0
        var lvIndex = 0 // local variable index

        // the first variable is the class instance if it's not static, so offset it
        if ((mod and Opcodes.ACC_STATIC) == 0) lvIndex++

        val argTypes = type.argumentTypes
        append(
            argTypes.joinToString { arg ->
                val currArgIndex = argIndex++

                return@joinToString buildString {
                    append(arg.format(remapper, isVarargs = currArgIndex == (argTypes.size - 1) && (mod and Opcodes.ACC_VARARGS) != 0))

                    if (generateNamedParameters) {
                        append(' ')

                        append(
                            this@formatDescriptor.getArg(-1, lvIndex, null)
                                ?.let(remapper.nameRemapper.mapper)
                                ?: "arg$currArgIndex"
                        )
                    }

                    lvIndex += arg.size // increment by the appropriate LVT size
                }
            }
        )
        append(')')
    }

    return MethodDeclaration(
        null,
        args,
        type.returnType.format(remapper),
        null
    )
}

/**
 * Formats a **non-generic** type with links and remaps any class names in it.
 *
 * @param remapper the name remapper
 * @param isVarargs whether this is the last parameter of a method and the last array dimension should be made into a variadic parameter
 * @return the formatted type
 */
fun Type.format(remapper: ContextualElementRemapper, isVarargs: Boolean = false): String {
    return when (sort) {
        Type.ARRAY -> buildString {
            append(remapper.mapAndLink(elementType.className.toInternalName()))

            var arrayDimensions = dimensions
            if (isVarargs) arrayDimensions--

            append("[]".repeat(arrayDimensions))
            if (isVarargs) append("...")
        }

        // Type#INTERNAL, it's private, so we need to use the value directly
        Type.OBJECT, 12 -> remapper.mapAndLink(internalName)
        else -> className
    }
}
