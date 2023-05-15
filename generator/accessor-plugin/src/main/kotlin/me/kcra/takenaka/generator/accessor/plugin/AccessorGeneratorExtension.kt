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

package me.kcra.takenaka.generator.accessor.plugin

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.model.ConstructorAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.objectweb.asm.Type
import kotlin.reflect.KClass

/**
 * A Gradle-specific builder for [AccessorConfiguration] with Minecraft presets.
 *
 * @property project the project
 * @author Matouš Kučera
 */
abstract class AccessorGeneratorExtension(val project: Project) {
    /**
     * Versions to be mapped.
     */
    abstract val versions: ListProperty<String>

    /**
     * The output directory, defaults to `build/takenaka/output`.
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * The cache directory, defaults to `build/takenaka/cache`.
     */
    abstract val cacheDirectory: DirectoryProperty

    /**
     * Whether cache should be validated strictly, defaults to false.
     */
    abstract val strictCache: Property<Boolean>

    /**
     * Class accessor models.
     */
    abstract val accessors: ListProperty<ClassAccessor>

    /**
     * Base package of the generated accessors, required.
     */
    abstract val basePackage: Property<String>

    /**
     * The language of the generated code, defaults to [LanguageFlavor.JAVA].
     */
    abstract val languageFlavor: Property<LanguageFlavor>

    /**
     * Namespaces that should be used in accessors, empty if all namespaces should be used.
     */
    abstract val accessedNamespaces: ListProperty<String>

    init {
        outputDirectory.convention(project.layout.buildDirectory.dir("takenaka/output"))
        cacheDirectory.convention(project.layout.buildDirectory.dir("takenaka/cache"))
        languageFlavor.convention(LanguageFlavor.JAVA)
        strictCache.convention(false)
    }

    /**
     * Creates a new accessor model with the supplied name.
     *
     * @param name the mapped class name
     * @param block the builder action
     */
    fun mapClass(name: String, block: ClassAccessorBuilder.() -> Unit = {}) {
        accessors.add(ClassAccessorBuilder(name).apply(block).toClassAccessor())
    }
}

/**
 * A builder for [ClassAccessor].
 *
 * @property name mapped name of the accessed class
 * @author Matouš Kučera
 */
class ClassAccessorBuilder(val name: String) {
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
     * Adds a new field accessor model.
     *
     * @param name the mapped field name
     * @param type the mapped field descriptor
     */
    fun field(name: String, type: String) {
        fields += FieldAccessor(name, type)
    }

    /**
     * Adds a new field accessor model.
     *
     * @param name the mapped field name
     * @param version the version of the [name] declaration, latest if null
     */
    fun field(name: String, version: Version? = null) {
        fields += FieldAccessor(name, null, version)
    }

    /**
     * Adds a new constructor accessor model.
     *
     * @param type the mapped constructor descriptor (method descriptor with a void return type)
     */
    fun constructor(type: String) {
        constructors += ConstructorAccessor(type)
    }

    /**
     * Adds a new constructor accessor model.
     *
     * @param parameters the mapped constructor parameters, converted with [Any.asDescriptor]
     */
    fun constructor(vararg parameters: Any) {
        constructors += ConstructorAccessor("(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})V")
    }

    /**
     * Adds a new method accessor model.
     *
     * @param name the mapped method name
     * @param type the mapped method descriptor
     */
    fun method(name: String, type: String) {
        methods += MethodAccessor(name, type)
    }

    /**
     * Adds a new method accessor model.
     *
     * @param name the mapped method name
     * @param returnType the mapped return type
     * @param parameters the mapped method parameters, converted with [Any.asDescriptor]
     */
    fun method(name: String, returnType: Any, vararg parameters: Any) {
        methods += MethodAccessor(name, "(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})${returnType.asDescriptor()}")
    }

    /**
     * Adds a new method accessor model.
     *
     * @param name the mapped method name
     * @param version the version of the [name] and [parameters] declaration, latest if null
     * @param parameters the mapped method parameters, converted with [Any.asDescriptor]
     */
    fun method(name: String, version: Version? = null, vararg parameters: Any) {
        methods += MethodAccessor(name, "(${parameters.joinToString(separator = "", transform = Any::asDescriptor)})", version)
    }

    /**
     * Creates a [ClassAccessor] out of this builder.
     *
     * @return the class accessor
     */
    fun toClassAccessor() = ClassAccessor(name, fields, constructors, methods)
}

/**
 * Tries to convert an object into a class type descriptor (`Lpackage/ClassName;`).
 *
 * This method accounts for multidimensional array types.
 *
 * @return the descriptor
 */
internal fun Any.asDescriptor(): String = when (this) {
    is Class<*> -> Type.getDescriptor(this)
    is KClass<*> -> Type.getDescriptor(this.java)
    is String -> {
        val componentType = this.replace("[]", "").toInternalName()
        val dimensions = (this.length - componentType.length) / 2

        "${"[".repeat(dimensions)}L$componentType;"
    }
    else -> throw IllegalArgumentException("Could not read parameter of type ${this.javaClass.name}")
}
