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
import me.kcra.takenaka.generator.web.*
import me.kcra.takenaka.generator.web.components.*
import me.kcra.takenaka.generator.web.util.*
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Generates a class overview page.
 *
 * @param klass the class
 * @param type the class type
 * @param hash the history file hash
 * @param nmsVersion the CraftBukkit NMS version string
 * @param workspace the workspace
 * @param friendlyNameRemapper the remapper for remapping signatures
 * @return the generated document
 */
fun GenerationContext.classPage(
    klass: MappingTreeView.ClassMappingView,
    type: ClassType,
    hash: String?,
    nmsVersion: String?,
    workspace: VersionedWorkspace,
    friendlyNameRemapper: ElementRemapper
): String = buildHTML {
    val klassName = getFriendlyDstName(klass)
    val friendlyKlassName = klassName.fromInternalName()

    val versionRootPath = getClassRelativeVersionRoot(klassName)
    val rootPath = "../$versionRootPath"

    val remapper = ContextualElementRemapper(friendlyNameRemapper, null, generator.config.index) { name ->
        getClassRelativePath(klassName, name)
    }

    val klassDeclaration = klass.formatDescriptor(friendlyKlassName, remapper)

    html(lang = "en") {
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
            title {
                append("${workspace.version.id} - $friendlyKlassName")
            }
        }
        body {
            navPlaceholderComponent()
            main {
                a(href = "index.html") {
                    append(friendlyKlassName.substringBeforeLast('.'))
                }

                div(classes = "class-header") {
                    p {
                        append(klassDeclaration.modifiersAndName)
                        klassDeclaration.formals?.let(::append)
                    }
                    if (hash != null) {
                        a(classes = "history-icon", href = "${rootPath}history/$hash.html", ariaLabel = "History") {
                            append("""<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>""")
                        }
                    }
                }
                if (klassDeclaration.superTypes != null) {
                    p(classes = "class-description") {
                        append(klassDeclaration.superTypes)
                    }
                }

                if (klassDeclaration.hasVisibleSuperClass) { // don't show superinterfaces when there's no actual superclass
                    val superInterfaces = klass.resolveSuperTypes(InheritanceWalkMode.INTERFACES)
                    if (superInterfaces.isNotEmpty()) {
                        p(classes = "interfaces-header") {
                            append("All mapped superinterfaces:")
                        }
                        p(classes = "interfaces-description") {
                            superInterfaces.forEachIndexed { i, s ->
                                val friendlyName = getFriendlyDstName(s)

                                a(href = getClassRelativePath(klassName, friendlyName)) {
                                    append(friendlyName.substringAfterLast('/'))
                                }
                                if (i != superInterfaces.lastIndex) append(", ")
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
                                    append(name.fromInternalName())
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

                val (enumFields, fields) = klass.fields.partition { (it.modifiers and Opcodes.ACC_ENUM) != 0 }

                if (enumFields.isNotEmpty()) {
                    addContentSpacer()
                    h4 {
                        append("Enum constant summary")
                    }
                    table(classes = "styled-table") {
                        thead {
                            tr {
                                th {
                                    append("Enum Constant")
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
                                                                    append(name)
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
                        append("Field summary")
                    }
                    table(classes = "styled-table styled-mobile-table") {
                        thead {
                            tr {
                                th {
                                    append("Modifier and Type")
                                }
                                th {
                                    append("Field")
                                }
                            }
                        }
                        tbody {
                            fields.forEach { field ->
                                tr {
                                    td(classes = "modifier-value") {
                                        append(field.modifiers.formatModifiers(ModifierMask.FIELD, type))
                                        append(field.formatDescriptor(remapper))
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
                                                                    append(name)
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

                val (constructors, methods) = klass.methods.partition(MappingTreeView.MethodMappingView::isConstructor)

                if (constructors.isNotEmpty()) {
                    addContentSpacer()
                    h4 {
                        append("Constructor summary")
                    }
                    table(classes = "styled-table") {
                        thead {
                            tr {
                                th {
                                    append("Modifier")
                                }
                                th {
                                    append("Constructor")
                                }
                            }
                        }
                        tbody {
                            constructors.forEach { ctor ->
                                val ctorMod = ctor.modifiers
                                tr {
                                    td(classes = "modifier-value") {
                                        append(ctorMod.formatModifiers(ModifierMask.CONSTRUCTOR, type))
                                    }
                                    td(classes = "constructor-value") {
                                        val ctorDeclaration = ctor.formatDescriptor(remapper, ctorMod)

                                        ctorDeclaration.formals?.let(::append)
                                        append(ctorDeclaration.args)
                                        ctorDeclaration.exceptions?.let { append(" throws $it") }
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
                val nonImplicitMethods = if (type == ClassType.ENUM) methods.filterNot { it.isEnumValueOf || it.isEnumValues } else methods

                if (nonImplicitMethods.isNotEmpty()) {
                    addContentSpacer()
                    h4 {
                        append("Method summary")
                    }
                    table(classes = "styled-table styled-mobile-table") {
                        thead {
                            tr {
                                th {
                                    append("Modifier and Type")
                                }
                                th {
                                    append("Method")
                                }
                            }
                        }
                        tbody {
                            nonImplicitMethods.forEach { method ->
                                val methodMod = method.modifiers
                                tr {
                                    td(classes = "modifier-value") {
                                        append(methodMod.formatModifiers(ModifierMask.METHOD, type))

                                        val methodDeclaration = method.formatDescriptor(remapper, methodMod)
                                        methodDeclaration.formals?.let { append("$it ") }
                                        append(methodDeclaration.returnType)
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
                                                                    remapper.nameRemapper = ElementRemapper(method.tree) { it.getName(id)?.replaceCraftBukkitNMSVersion(namespacedNmsVersion) }
                                                                    remapper.linkRemapper = friendlyNameRemapper

                                                                    val methodDeclaration = method.formatDescriptor(remapper, methodMod)
                                                                    remapper.reset()

                                                                    append(methodName)
                                                                    append(methodDeclaration.args)
                                                                    methodDeclaration.exceptions?.let { append(" throws $it") }
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

    val classMask = if ((mod and Opcodes.ACC_INTERFACE) != 0) ModifierMask.INTERFACE else ModifierMask.CLASS
    val modifiersAndName = buildString {
        append(mod.formatModifiers(classMask))
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

        var lvIndex = 0 // local variable index

        // the first variable is the class instance if it's not static, so offset it
        if ((mod and Opcodes.ACC_STATIC) == 0) lvIndex++

        val argTypes = type.argumentTypes
        argTypes.forEachIndexed { argIndex, arg ->
            val isLast = argIndex == argTypes.lastIndex

            append(arg.format(remapper, isVarargs = isLast && (mod and Opcodes.ACC_VARARGS) != 0))
            if (generateNamedParameters) {
                append(' ')

                append(
                    this@formatDescriptor.getArg(-1, lvIndex, null)
                        ?.let(remapper.nameRemapper.mapper)
                        ?.escapeHtml()
                        ?: "arg$argIndex"
                )
            }

            lvIndex += arg.size // increment by the appropriate LVT size
            if (!isLast) append(", ")
        }
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

/**
 * Formats a modifier integer into a string.
 *
 * Additional formatting is applied based on the [mask] and [parentType],
 * don't specify them if you want a plain modifier string without any masking or removal of implicit modifiers.
 *
 * @param mask the modifier mask
 * @param parentType the type of the class containing the member, **should only be specified if formatting modifiers for a class member**
 * @return the modifier string, **may end with a space**
 */
fun Int.formatModifiers(mask: ModifierMask = ModifierMask.NONE, parentType: ClassType = ClassType.CLASS): String = buildString {
    val mMod = this@formatModifiers and mask

    if ((mMod and Opcodes.ACC_PUBLIC) != 0) {
        if (parentType == ClassType.INTERFACE) {
            // interface fields are implicitly public
            // see: https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.3

            // substitute the public modifier on implemented non-static interface methods with the default modifier
            // remove the public modifier on other interface methods, implicit
            // see: https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.4
            if (mask == ModifierMask.METHOD && (mMod and Opcodes.ACC_ABSTRACT) == 0 && (mMod and Opcodes.ACC_STATIC) == 0) {
                append("default ")
            }
        } else {
            append("public ")
        }
    }
    if ((mMod and Opcodes.ACC_PRIVATE) != 0) append("private ")
    if ((mMod and Opcodes.ACC_PROTECTED) != 0) append("protected ")

    // interface fields are implicitly static
    // see: https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.3
    if ((mMod and Opcodes.ACC_STATIC) != 0 && (mask != ModifierMask.FIELD || parentType != ClassType.INTERFACE)) {
        append("static ")
    }

    // enums can have an abstract modifier (methods included) if its constants have a custom impl
    // TODO: should we remove that?

    // an interface and possibly some of its methods are implicitly abstract
    // we need to check the unmasked modifiers here, since ACC_INTERFACE is not among Modifier#interfaceModifiers
    if (
        (mMod and Opcodes.ACC_ABSTRACT) != 0
        && (mask != ModifierMask.INTERFACE || (this@formatModifiers and Opcodes.ACC_INTERFACE) == 0)
        && (mask != ModifierMask.METHOD || parentType != ClassType.INTERFACE)
    ) {
        append("abstract ")
    }
    if ((mMod and Opcodes.ACC_FINAL) != 0) {
        // interface fields are implicitly final
        // see: https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.3

        // enums and records are implicitly final
        // we need to check the unmasked modifiers here, since ACC_ENUM is not among Modifier#classModifiers
        if (
            (mask != ModifierMask.FIELD || parentType != ClassType.INTERFACE)
            && (mask != ModifierMask.CLASS || ((this@formatModifiers and Opcodes.ACC_ENUM) == 0 && (this@formatModifiers and Opcodes.ACC_RECORD) == 0))
        ) {
            append("final ")
        }
    }
    if ((mMod and Opcodes.ACC_NATIVE) != 0) append("native ")
    if ((mMod and Opcodes.ACC_STRICT) != 0) append("strict ")
    if ((mMod and Opcodes.ACC_SYNCHRONIZED) != 0) append("synchronized ")
    if ((mMod and Opcodes.ACC_TRANSIENT) != 0) append("transient ")
    if ((mMod and Opcodes.ACC_VOLATILE) != 0) append("volatile ")
}
