/*
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Modifications to this file are licensed under the Apache License, Version 2.0.
 */

package me.kcra.takenaka.generator.web

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.ElementRemapper
import me.kcra.takenaka.core.mapping.fromInternalName
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

/**
 * Formatting options.
 */
typealias FormattingOptions = Int

/**
 * Checks if the options contain an option.
 *
 * @param option the option to check
 * @return do the options contain the option?
 */
operator fun FormattingOptions.contains(option: Int): Boolean = (this and option) != 0

/**
 * Creates formatting options from multiple options.
 *
 * @param options the options
 * @return the formatting options
 */
fun formattingOptionsOf(vararg options: Int): FormattingOptions = options.reduceOrNull { v1, v2 -> v1 or v2 } ?: 0

/**
 * A builder for [FormattingOptions].
 *
 * @property value the integer value, internal use only (for adding additional options)
 * @author Matouš Kučera
 */
class FormattingOptionsBuilder(var value: FormattingOptions = 0) {
    /**
     * Appends the [DefaultFormattingOptions.ESCAPE_HTML_SYMBOLS] option.
     */
    fun escapeHtmlSymbols() {
        value = value or DefaultFormattingOptions.ESCAPE_HTML_SYMBOLS
    }

    /**
     * Appends the [DefaultFormattingOptions.INTERFACE_SIGNATURE] option.
     */
    fun interfaceSignature() {
        value = value or DefaultFormattingOptions.INTERFACE_SIGNATURE
    }

    /**
     * Appends the [DefaultFormattingOptions.GENERATE_NAMED_PARAMETERS] option.
     */
    fun generateNamedParameters() {
        value = value or DefaultFormattingOptions.GENERATE_NAMED_PARAMETERS
    }

    /**
     * Appends the [DefaultFormattingOptions.VARIADIC_PARAMETER] option.
     */
    fun variadicParameter() {
        value = value or DefaultFormattingOptions.VARIADIC_PARAMETER
    }

    /**
     * Checks if the options contain an option.
     *
     * @param option the option to check
     * @return do the options contain the option?
     */
    operator fun contains(option: Int): Boolean = (value and option) != 0

    /**
     * Returns the formatting options.
     *
     * @return the value
     */
    fun toFormattingOptions(): FormattingOptions = value
}

/**
 * Creates formatting options from a builder.
 *
 * @param block the builder action
 * @return the formatting options
 */
inline fun buildFormattingOptions(block: FormattingOptionsBuilder.() -> Unit): FormattingOptions = FormattingOptionsBuilder().apply(block).toFormattingOptions()

/**
 * A group of formatting options used by [SignatureFormatter].
 */
object DefaultFormattingOptions {
    /**
     * Requests the formatter to escape less than and greater than signs (formal type parameter declaration) to HTML format.
     */
    const val ESCAPE_HTML_SYMBOLS = 0x00000001

    /**
     * Requests the formatter to use the `extends` separator for superinterfaces, most likely because the visited signature belongs to an interface.
     */
    const val INTERFACE_SIGNATURE = 0x00000010

    /**
     * Requests the formatter to generate argument names in the form of `arg%argument index%`.
     */
    const val GENERATE_NAMED_PARAMETERS = 0x00000100

    /**
     * Requests the formatter to make the last argument a variadic one, if it's an array.
     */
    const val VARIADIC_PARAMETER = 0x00001000
}

/**
 * A [SignatureVisitor] implementation that builds the Java generic type declaration corresponding to the signature it visits.
 *
 * This is adapted from the [org.objectweb.asm.util.TraceSignatureVisitor] class.
 *
 * **NOTE:** java/lang/Object and java/lang/Record superclasses are omitted automatically, **but java/lang/Enum is not**.
 *
 * @author Eugene Kuleshov
 * @author Eric Bruneton
 * @author Matouš Kučera
 */
class SignatureFormatter : SignatureVisitor {
    /** Whether the visited signature is a class signature of a Java interface.  */
    private val isInterface: Boolean

    /** The Java generic type declaration corresponding to the visited signature.  */
    private val declaration_: StringBuilder

    /** The Java generic method return type declaration corresponding to the visited signature.  */
    private var returnType_: StringBuilder? = null

    /** The Java generic exception types declaration corresponding to the visited signature.  */
    private var exceptions_: StringBuilder? = null

    /** Whether [.visitFormalTypeParameter] has been called.  */
    private var formalTypeParameterVisited = false

    /** Whether [.visitInterfaceBound] has been called.  */
    private var interfaceBoundVisited = false

    /** Whether [.visitParameterType] has been called.  */
    private var parameterTypeVisited = false

    /** Whether [.visitInterface] has been called.  */
    private var interfaceVisited = false

    /**
     * The stack used to keep track of class types that have arguments. Each element of this stack is
     * a boolean encoded in one bit. The top of the stack is the least significant bit. Pushing false
     * = *2, pushing true = *2+1, popping = /2.
     */
    private var argumentStack = 0

    /**
     * The stack used to keep track of array class types. Each element of this stack is a boolean
     * encoded in one bit. The top of the stack is the lowest order bit. Pushing false = *2, pushing
     * true = *2+1, popping = /2.
     */
    private var arrayStack = 0

    /** The separator to append before the next visited class or inner class type.  */
    private var separator = ""

    // remapping stuff

    /**
     * The class name remapper.
     */
    private val remapper: ElementRemapper?

    /**
     * The link remapper.
     */
    private val linkRemapper: Remapper?

    /**
     * The package index used for looking up foreign class references.
     */
    private val packageIndex: ClassSearchIndex?

    /**
     * The version of the mappings.
     */
    private val version: Version?

    private val classNames: MutableList<String> = mutableListOf()

    // remapping stuff end

    /**
     * The formatting options.
     */
    val options: FormattingOptions

    /**
     * The index where the main formals end.
     */
    var formalEndIndex: Int = -1

    /**
     * The index of the next argument.
     */
    var argumentIndex: Int = 0

    /**
     * The formal type parameters of the visited class signature.
     */
    val formals: String
        get() = if (formalEndIndex != -1) declaration_.substring(0, formalEndIndex) else ""

    /**
     * The supertypes of the visited class signature.
     */
    val superTypes: String
        get() = (if (formalEndIndex != -1) declaration_.substring(formalEndIndex) else declaration).trim()

    /**
     * The entire rebuilt signature.
     */
    val declaration: String
        get() = declaration_.toString()

    /**
     * The return type of the visited method signature.
     */
    val returnType: String?
        get() = returnType_?.toString()

    /**
     * The throws clause of the visited method signature.
     */
    val exceptions: String?
        get() = exceptions_?.toString()

    /**
     * The parameters of the visited method signature.
     */
    val args: String
        get() {
            val startIndex = declaration_.indexOf('(')
            val endIndex = declaration_.indexOf(')', startIndex)

            return declaration_.substring(startIndex, endIndex + 1)
        }

    private val formalStartStr: String
        get() = if (DefaultFormattingOptions.ESCAPE_HTML_SYMBOLS in options) "&lt;" else "<"
    private val formalEndStr: String
        get() = if (DefaultFormattingOptions.ESCAPE_HTML_SYMBOLS in options) "&gt;" else ">"

    /**
     * Constructs a new [SignatureFormatter].
     *
     * @param options the formatting options
     */
    constructor(
        options: FormattingOptions,
        remapper: ElementRemapper? = null,
        linkRemapper: Remapper? = null,
        packageIndex: ClassSearchIndex? = null,
        version: Version? = null
    ) : super(Opcodes.ASM9) {
        isInterface = DefaultFormattingOptions.INTERFACE_SIGNATURE in options
        declaration_ = StringBuilder()

        this.options = options
        this.remapper = remapper
        this.linkRemapper = linkRemapper
        this.packageIndex = packageIndex
        this.version = version
    }

    private constructor(
        options: FormattingOptions,
        remapper: ElementRemapper?,
        linkRemapper: Remapper?,
        packageIndex: ClassSearchIndex?,
        version: Version?,
        stringBuilder: StringBuilder
    ) : super(Opcodes.ASM9) {
        isInterface = false
        declaration_ = stringBuilder

        this.options = options
        this.remapper = remapper
        this.linkRemapper = linkRemapper
        this.packageIndex = packageIndex
        this.version = version
    }

    override fun visitFormalTypeParameter(name: String) {
        declaration_.append(if (formalTypeParameterVisited) COMMA_SEPARATOR else formalStartStr).append(name)
        formalTypeParameterVisited = true
        interfaceBoundVisited = false
    }

    override fun visitClassBound(): SignatureVisitor {
        separator = EXTENDS_SEPARATOR
        startType()
        return this
    }

    override fun visitInterfaceBound(): SignatureVisitor {
        separator = if (interfaceBoundVisited) COMMA_SEPARATOR else EXTENDS_SEPARATOR
        interfaceBoundVisited = true
        startType()
        return this
    }

    override fun visitSuperclass(): SignatureVisitor {
        endFormals()
        separator = EXTENDS_SEPARATOR
        startType()
        return this
    }

    override fun visitInterface(): SignatureVisitor {
        if (interfaceVisited) {
            separator = COMMA_SEPARATOR
        } else {
            separator = if (isInterface) EXTENDS_SEPARATOR else IMPLEMENTS_SEPARATOR
            interfaceVisited = true
        }
        startType()
        return this
    }

    override fun visitParameterType(): SignatureVisitor {
        endFormals()
        if (parameterTypeVisited) {
            appendArgumentName()
            declaration_.append(COMMA_SEPARATOR)
        } else {
            declaration_.append('(')
            parameterTypeVisited = true
        }
        startType()
        return this
    }

    override fun visitReturnType(): SignatureVisitor {
        endFormals()
        if (parameterTypeVisited) {
            parameterTypeVisited = false
            appendArgumentName()
        } else {
            declaration_.append('(')
        }
        declaration_.append(')')
        val returnType0 = StringBuilder()
        returnType_ = returnType0
        return SignatureFormatter(options, remapper, linkRemapper, packageIndex, version, returnType0)
    }

    override fun visitExceptionType(): SignatureVisitor =
        SignatureFormatter(options, remapper, linkRemapper, packageIndex, version, exceptions_?.append(COMMA_SEPARATOR) ?: StringBuilder().also { exceptions_ = it })

    override fun visitBaseType(descriptor: Char) {
        val baseType = BASE_TYPES[descriptor] ?: throw IllegalArgumentException()
        declaration_.append(baseType)
        endType()
    }

    override fun visitTypeVariable(name: String) {
        declaration_.append(separator).append(name)
        separator = ""
        endType()
    }

    override fun visitArrayType(): SignatureVisitor {
        startType()
        arrayStack = arrayStack or 1
        return this
    }

    override fun visitClassType(name: String) {
        classNames.add(name)

        if (name == "java/lang/Object" || name == "java/lang/Record") {
            // 'Map<java.lang.Object,java.util.List>' or 'abstract public V get(Object key);' should have
            // Object 'but java.lang.String extends java.lang.Object' is unnecessary.
            val needClass = argumentStack % 2 != 0 || parameterTypeVisited
            if (needClass) {
                declaration_.append(separator).append(remapper?.mapAndLink(name, version!!, packageIndex, linkRemapper) ?: name)
            }
        } else {
            declaration_.append(separator).append(remapper?.mapAndLink(name, version!!, packageIndex, linkRemapper) ?: name)
        }
        separator = ""
        argumentStack *= 2
    }

    override fun visitInnerClassType(name: String) {
        val outerClassName = classNames.removeLast()
        val className = "$outerClassName$$name"
        classNames += className
        val remappedOuter = (remapper?.mapType(outerClassName) ?: outerClassName) + '$'
        val remappedName = remapper?.mapType(className) ?: className
        val index = if (remappedName.startsWith(remappedOuter)) {
            remappedOuter.length
        } else {
            remappedName.lastIndexOf('$') + 1
        }

        if (argumentStack % 2 != 0) {
            declaration_.append(formalEndStr)
        }
        argumentStack /= 2
        declaration_.append('.')
        declaration_.append(separator).append(
            if (remappedName != className) {
                """<a href="/${version!!.id}/${linkRemapper?.mapType(className) ?: remappedName}.html">${remappedName.substring(index)}</a>"""
            } else {
                remappedName.substring(index)
            }
        )
        separator = ""
        argumentStack *= 2
    }

    override fun visitTypeArgument() {
        if (argumentStack % 2 == 0) {
            ++argumentStack
            declaration_.append(formalStartStr)
        } else {
            declaration_.append(COMMA_SEPARATOR)
        }
        declaration_.append('?')
    }

    override fun visitTypeArgument(tag: Char): SignatureVisitor {
        if (argumentStack % 2 == 0) {
            ++argumentStack
            declaration_.append(formalStartStr)
        } else {
            declaration_.append(COMMA_SEPARATOR)
        }
        if (tag == EXTENDS) {
            declaration_.append("? extends ")
        } else if (tag == SUPER) {
            declaration_.append("? super ")
        }
        startType()
        return this
    }

    override fun visitEnd() {
        if (argumentStack % 2 != 0) {
            declaration_.append(formalEndStr)
        }
        argumentStack /= 2
        endType()
        classNames.removeLast()
    }

    private fun endFormals() {
        if (formalTypeParameterVisited) {
            declaration_.append(formalEndStr)
            if (formalEndIndex == -1 && argumentStack % 2 == 0) {
                formalEndIndex = declaration_.length
            }
            formalTypeParameterVisited = false
        }
    }

    private fun startType() {
        arrayStack *= 2
    }

    private fun endType() {
        if (arrayStack % 2 == 0) {
            arrayStack /= 2
        } else {
            while (arrayStack % 2 != 0) {
                arrayStack /= 2
                declaration_.append("[]")
            }
        }
    }

    private fun appendArgumentName() {
        if (DefaultFormattingOptions.GENERATE_NAMED_PARAMETERS in options) {
            if (!parameterTypeVisited && DefaultFormattingOptions.VARIADIC_PARAMETER in options && declaration_.endsWith("[]")) {
                declaration_.setLength(declaration_.length - 2)
                declaration_.append("...")
            }
            declaration_.append(" arg${argumentIndex++}")
        }
    }

    companion object {
        private const val COMMA_SEPARATOR = ", "
        private const val EXTENDS_SEPARATOR = " extends "
        private const val IMPLEMENTS_SEPARATOR = " implements "
        private val BASE_TYPES: Map<Char, String> = mapOf(
            'Z' to "boolean",
            'B' to "byte",
            'C' to "char",
            'S' to "short",
            'I' to "int",
            'J' to "long",
            'F' to "float",
            'D' to "double",
            'V' to "void",
        )
    }
}

/**
 * Remaps a class name and creates a link if a mapping has been found.
 *
 * @param internalName the internal name of the class to be remapped
 * @param version the mapping version
 * @param packageIndex the index used for looking up foreign class references
 * @return the remapped class name, a link if it was found
 */
fun ElementRemapper.mapAndLink(internalName: String, version: Version, packageIndex: ClassSearchIndex? = null, linkRemapper: Remapper? = null): String {
    val foreignUrl = packageIndex?.linkClass(internalName)
    if (foreignUrl != null) {
        return """<a href="$foreignUrl">${internalName.substringAfterLast('/')}</a>"""
    }

    val remappedName = tree.getClass(internalName)?.let(elementMapper)
        ?: return internalName.fromInternalName()
    val linkName = linkRemapper?.map(internalName) ?: remappedName

    return """<a href="/${version.id}/$linkName.html">${remappedName.substringAfterLast('/')}</a>"""
}

/**
 * Makes a new [SignatureFormatter] and immediately visits the signature to it.
 *
 * @param options the formatting options
 * @param remapper the [ElementRemapper] used for remapping names
 * @param linkRemapper the [Remapper] used for remapping links
 * @param packageIndex the index used for resolving foreign class references
 * @param version the mapping's version
 * @return the formatter
 * @see SignatureReader.accept
 */
fun String.formatSignature(
    options: FormattingOptions,
    remapper: ElementRemapper? = null,
    linkRemapper: Remapper? = null,
    packageIndex: ClassSearchIndex? = null,
    version: Version? = null
): SignatureFormatter {
    val visitor = SignatureFormatter(options, remapper, linkRemapper, packageIndex, version)
    SignatureReader(this).accept(visitor)

    return visitor
}

/**
 * Makes a new [SignatureFormatter] and immediately visits the signature to it as a type signature.
 *
 * @param options the formatting options
 * @param remapper the [ElementRemapper] used for remapping names
 * @param linkRemapper the [Remapper] used for remapping links
 * @param packageIndex the index used for resolving foreign class references
 * @param version the mapping's version
 * @return the formatter
 * @see SignatureReader.acceptType
 */
fun String.formatTypeSignature(
    options: FormattingOptions,
    remapper: ElementRemapper? = null,
    linkRemapper: Remapper? = null,
    packageIndex: ClassSearchIndex? = null,
    version: Version? = null
): SignatureFormatter {
    val visitor = SignatureFormatter(options, remapper, linkRemapper, packageIndex, version)
    SignatureReader(this).acceptType(visitor)

    return visitor
}
