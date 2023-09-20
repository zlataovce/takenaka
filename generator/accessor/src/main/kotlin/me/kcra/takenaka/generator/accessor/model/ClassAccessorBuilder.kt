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

package me.kcra.takenaka.generator.accessor.model

import me.kcra.takenaka.core.mapping.toInternalName
import org.objectweb.asm.Type
import java.util.*
import kotlin.reflect.KClass

/**
 * A base class for a [ClassAccessor] builder.
 *
 * @property name mapped name of the accessed class
 * @author Matouš Kučera
 */
abstract class AbstractClassAccessorBuilder(val name: String) {
    /**
     * Field accessor models.
     */
    var fields = mutableListOf<FieldAccessor>()

    /**
     * Constructor accessor models.
     */
    var constructors = mutableListOf<ConstructorAccessor>()

    /**
     * Method accessor models.
     */
    var methods = mutableListOf<MethodAccessor>()

    /**
     * Member types required in bulk.
     */
    var requiredMemberTypes = 0

    /**
     * Adds a new field accessor model with an explicitly defined type.
     *
     * **The type and name must both be mapped by the same namespace, else generation will fail!**
     * (e.g. both [type] and [name] must be Mojang names)
     *
     * @param name the mapped field name
     * @param type the mapped field type, converted with [Any.asDescriptor]
     */
    fun field(type: Any, name: String) {
        fields += FieldAccessor(name, type.asDescriptor())
    }

    /**
     * Adds a new field accessor model with an inferred type.
     *
     * @param name the mapped field name
     * @param version the version of the [name] declaration, latest if null
     */
    @JvmOverloads
    fun fieldInferred(name: String, version: String? = null) {
        fields += FieldAccessor(name, null, version)
    }

    /**
     * Adds a new chained field accessor model.
     *
     * @param block the builder action
     */
    protected inline fun fieldChain0(block: FieldChainBuilder.() -> Unit) {
        fields += buildFieldChain(block)
    }

    /**
     * Adds new field accessor models with a type that matches [ClassAccessorBuilder.name].
     *
     * **The names must all be mapped by the same namespace as [ClassAccessorBuilder.name], else generation will fail!**
     * (e.g. both [ClassAccessorBuilder.name] and [name] must be Mojang names)
     *
     * @param names the mapped enum constant names
     */
    fun enumConstant(vararg names: String) {
        names.forEach { name ->
            field(this.name, name)
        }
    }

    /**
     * Adds a new chained field accessor model with a type that matches [ClassAccessorBuilder.name].
     *
     * **The names must all be mapped by the same namespace as [ClassAccessorBuilder.name], else generation will fail!**
     * (e.g. both [ClassAccessorBuilder.name] and [name] must be Mojang names)
     *
     * @param names the mapped enum constant names to be chained
     */
    fun enumConstantChain(vararg names: String) {
        fieldChain0 {
            names.forEach { name ->
                item(this@AbstractClassAccessorBuilder.name, name)
            }
        }
    }

    /**
     * Adds a new constructor accessor model.
     *
     * **The parameter types must all be mapped by the same namespace, else generation will fail!**
     * (e.g. all types in [parameters] must be Mojang names)
     *
     * @param parameters the mapped constructor parameters, converted with [Any.asDescriptor]
     */
    fun constructor(vararg parameters: Any) {
        constructors += ConstructorAccessor("(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})V")
    }

    /**
     * Adds a new method accessor model with an explicitly defined return type.
     *
     * **The return type, method name and parameter types must all be mapped by the same namespace, else generation will fail!**
     * (e.g. [returnType], [name] and all types in [parameters] must be Mojang names)
     *
     * @param returnType the mapped return type
     * @param name the mapped method name
     * @param parameters the mapped method parameters, converted with [Any.asDescriptor]
     */
    fun method(returnType: Any, name: String, vararg parameters: Any) {
        methods += MethodAccessor(name, "(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})${returnType.asDescriptor()}")
    }

    /**
     * Adds a new method accessor model with an inferred return type.
     *
     * **The method name and parameter types must both be mapped by the same namespace, else generation will fail!**
     * (e.g. [name] and all types in [parameters] must be Mojang names)
     *
     * @param name the mapped method name
     * @param version the version of the [name] and [parameters] declaration, latest if null
     * @param parameters the mapped method parameters, converted with [Any.asDescriptor]
     */
    @JvmOverloads
    fun methodInferred(name: String, version: String? = null, vararg parameters: Any) {
        methods += MethodAccessor(
            name,
            "(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})",
            version
        )
    }

    /**
     * Adds a new chained method accessor model.
     *
     * @param block the builder action
     */
    protected inline fun methodChain0(block: MethodChainBuilder.() -> Unit) {
        methods += buildMethodChain(block)
    }

    /**
     * Adds a new getter method accessor model (`{type} get{name}()` descriptor).
     *
     * *This method does not account for boolean-abbreviated getters (`isSomething`).*
     *
     * @param name the mapped method name
     * @param version the version of the [name] declaration, latest if null
     */
    @JvmOverloads
    fun getterInferred(name: String, version: String? = null) {
        methodInferred("get${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}", version)
    }

    /**
     * Adds a new getter method accessor model (`{type} [is|get]{name}()` descriptor).
     *
     * *This method accounts for boolean-abbreviated getters (`isSomething`).*
     *
     * **The type and name must both be mapped by the same namespace, else generation will fail!**
     * (e.g. both [type] and [name] must be Mojang names)
     *
     * @param type the mapped getter type, converted with [Any.asDescriptor]
     * @param name the mapped method name
     */
    fun getter(type: Any, name: String) {
        val prefix = if (type.asDescriptor() == "Z") "is" else "get" // presume boolean getter name to be isSomething instead of getSomething

        method(type, "$prefix${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}")
    }

    /**
     * Adds a new chained getter method accessor model, useful for chaining together normal and record getters.
     *
     * *This method does not account for boolean-abbreviated getters (`isSomething`).*
     *
     * @param name the mapped method name
     * @param version the version of the [name] declaration, latest if null
     */
    @JvmOverloads
    fun getterChainInferred(name: String, version: String? = null) {
        methodChain0 {
            itemInferred(name, version)
            itemInferred("get${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}", version)
        }
    }

    /**
     * Adds a new chained getter method accessor model, useful for chaining together normal and record getters.
     *
     * *This method accounts for boolean-abbreviated getters (`isSomething`).*
     *
     * **The type and name must both be mapped by the same namespace, else generation will fail!**
     * (e.g. both [type] and [name] must be Mojang names)
     *
     * @param type the mapped getter type, converted with [Any.asDescriptor]
     * @param name the mapped method name
     */
    fun getterChain(type: Any, name: String) {
        val prefix = if (type.asDescriptor() == "Z") "is" else "get" // presume boolean getter name to be isSomething instead of getSomething

        methodChain0 {
            item(type, name)
            item(type, "$prefix${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}")
        }
    }

    /**
     * Adds a new setter method accessor model (`{type} set{name}({type})` descriptor).
     *
     * **The type and name must both be mapped by the same namespace, else generation will fail!**
     * (e.g. both [type] and [name] must be Mojang names)
     *
     * @param type the mapped setter type, converted with [Any.asDescriptor]
     * @param name the mapped method name
     */
    fun setter(type: Any, name: String) {
        method(Void.TYPE, "set${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}", type)
    }

    /**
     * Adds a new chained setter method accessor model, useful for chaining together normal and record setters.
     *
     * **The type and name must both be mapped by the same namespace, else generation will fail!**
     * (e.g. both [type] and [name] must be Mojang names)
     *
     * @param type the mapped setter type, converted with [Any.asDescriptor]
     * @param name the mapped method name
     */
    fun setterChain(type: Any, name: String) {
        methodChain0 {
            item(Void.TYPE, name, type)
            item(Void.TYPE, "set${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}", type)
        }
    }

    /**
     * Adds new required member types.
     *
     * @param options the member types
     * @see DefaultRequiredMemberTypes
     */
    fun memberTypes(vararg options: Int) {
        requiredMemberTypes = requiredMemberTypesOf(requiredMemberTypes, *options)
    }

    /**
     * Creates a [ClassAccessor] out of this builder.
     *
     * @return the class accessor
     */
    fun toClassAccessor() = ClassAccessor(name, fields, constructors, methods, requiredMemberTypes)
}

/**
 * A simple implementation of [AbstractClassAccessorBuilder].
 *
 * @param name mapped name of the accessed class
 */
class ClassAccessorBuilder(name: String) : AbstractClassAccessorBuilder(name) {
    /**
     * Adds a new chained field accessor model.
     *
     * @param block the builder action
     */
    fun fieldChain(block: FieldChainBuilder.() -> Unit) {
        fieldChain0(block)
    }

    /**
     * Adds a new chained method accessor model.
     *
     * @param block the builder action
     */
    fun methodChain(block: MethodChainBuilder.() -> Unit) {
        methodChain0(block)
    }
}

/**
 * Builds a new [ClassAccessor].
 *
 * @param name mapped name of the accessed class
 * @return the class accessor
 */
inline fun buildClassAccessor(name: String, block: ClassAccessorBuilder.() -> Unit): ClassAccessor =
    ClassAccessorBuilder(name).apply(block).toClassAccessor()

/**
 * A single chain step.
 */
typealias ChainStep<T> = (T?) -> T

/**
 * A builder for field mapping chains.
 *
 * @author Matouš Kučera
 */
open class FieldChainBuilder {
    /**
     * Members of this chain.
     */
    val steps = mutableListOf<ChainStep<FieldAccessor>>()

    /**
     * Adds a new field accessor model with an explicitly defined type to the chain.
     *
     * @param name the mapped field name
     * @param type the mapped field type, converted with [Any.asDescriptor]
     */
    fun item(type: Any, name: String) {
        steps += { last -> FieldAccessor(name, type.asDescriptor(), chain = last) }
    }

    /**
     * Adds a new field accessor model with an inferred type to the chain.
     *
     * @param name the mapped field name
     * @param version the version of the [name] declaration, latest if null
     */
    @JvmOverloads
    fun itemInferred(name: String, version: String? = null) {
        steps += { last -> FieldAccessor(name, null, version, chain = last) }
    }

    /**
     * Creates a [FieldAccessor] out of this builder.
     *
     * @return the field accessor
     */
    fun toFieldAccessor(): FieldAccessor {
        var currentAccessor: FieldAccessor? = null

        for (step in steps.reversed()) {
            currentAccessor = step(currentAccessor)
        }

        return requireNotNull(currentAccessor) {
            "Chain was empty"
        }
    }
}

/**
 * Builds a new [FieldAccessor] chain.
 *
 * @return the field accessor
 */
inline fun buildFieldChain(block: FieldChainBuilder.() -> Unit): FieldAccessor =
    FieldChainBuilder().apply(block).toFieldAccessor()

/**
 * A builder for method mapping chains.
 *
 * @author Matouš Kučera
 */
open class MethodChainBuilder {
    /**
     * Members of this chain.
     */
    val steps = mutableListOf<ChainStep<MethodAccessor>>()

    /**
     * Adds a new method accessor model with an explicitly defined return type to the chain.
     *
     * @param name the mapped method name
     * @param returnType the mapped return type
     * @param parameters the mapped method parameters, converted with [Any.asDescriptor]
     */
    fun item(returnType: Any, name: String, vararg parameters: Any) {
        steps += { last ->
            MethodAccessor(
                name,
                "(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})${returnType.asDescriptor()}",
                chain = last
            )
        }
    }

    /**
     * Adds a new method accessor model with an inferred return type to the chain.
     *
     * @param name the mapped method name
     * @param version the version of the [name] and [parameters] declaration, latest if null
     * @param parameters the mapped method parameters, converted with [Any.asDescriptor]
     */
    @JvmOverloads
    fun itemInferred(name: String, version: String? = null, vararg parameters: Any) {
        steps += { last ->
            MethodAccessor(
                name,
                "(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})",
                version,
                chain = last
            )
        }
    }

    /**
     * Creates a [MethodAccessor] out of this builder.
     *
     * @return the method accessor
     */
    fun toMethodAccessor(): MethodAccessor {
        var currentAccessor: MethodAccessor? = null

        for (step in steps.reversed()) {
            currentAccessor = step(currentAccessor)
        }

        return requireNotNull(currentAccessor) {
            "Chain was empty"
        }
    }
}

/**
 * Builds a new [MethodAccessor] chain.
 *
 * @return the method accessor
 */
inline fun buildMethodChain(block: MethodChainBuilder.() -> Unit): MethodAccessor =
    MethodChainBuilder().apply(block).toMethodAccessor()

/**
 * Tries to convert an object into a class type descriptor (`Lpackage/ClassName;`).
 *
 * This method accounts for multidimensional array types.
 *
 * @return the descriptor
 */
fun Any.asDescriptor(): String = when (this) {
    is Class<*> -> Type.getDescriptor(this)
    is KClass<*> -> Type.getDescriptor(this.java)
    is String -> {
        val componentType = this.replace("[]", "")
        val dimensions = (this.length - componentType.length) / 2
        val componentTypeDescriptor = when (componentType) {
            "boolean" -> "Z"
            "byte" -> "B"
            "short" -> "S"
            "int" -> "I"
            "long" -> "J"
            "float" -> "F"
            "double" -> "D"
            "char" -> "C"
            "void" -> "V"
            else -> "L${componentType.toInternalName()};"
        }

        "${"[".repeat(dimensions)}$componentTypeDescriptor"
    }
    else -> throw IllegalArgumentException("Could not read parameter of type ${this.javaClass.name}")
}
