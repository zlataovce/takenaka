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
import me.kcra.takenaka.core.VersionManifest
import me.kcra.takenaka.core.VersionRangeBuilder
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorFlavor
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.model.*
import me.kcra.takenaka.generator.accessor.plugin.tasks.DEFAULT_INDEX_NS
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.objectweb.asm.Type
import java.util.*
import kotlin.reflect.KClass

/**
 * A Gradle-specific builder for [AccessorConfiguration] with Minecraft presets.
 *
 * @property project the project
 * @author Matouš Kučera
 */
abstract class AccessorGeneratorExtension(internal val project: Project, internal val manifest: VersionManifest) {
    /**
     * Versions to be mapped.
     *
     * In case that a mapping bundle is selected (the `mappingBundle` configuration has exactly one file),
     * this property is used for selecting a version subset within the bundle
     * (every version from the bundle is mapped if no version is specified here).
     *
     * @see me.kcra.takenaka.generator.common.provider.impl.BundledMappingProvider.versions
     */
    abstract val versions: SetProperty<String>

    /**
     * The output directory, defaults to `build/takenaka/output`.
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * The cache directory, defaults to `build/takenaka/cache`.
     */
    abstract val cacheDirectory: DirectoryProperty

    /**
     * Whether output cache verification constraints should be relaxed, defaults to true.
     */
    abstract val relaxedCache: Property<Boolean>

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
     * The form of the generated accessors, defaults to [AccessorFlavor.NONE].
     */
    abstract val accessorFlavor: Property<AccessorFlavor>

    /**
     * Namespaces that should be used in accessors, empty if all namespaces should be used.
     */
    abstract val accessedNamespaces: ListProperty<String>

    /**
     * Namespaces that should be used for computing history, defaults to "mojang", "spigot", "searge" and "intermediary".
     */
    abstract val historyNamespaces: ListProperty<String>

    /**
     * Namespace that contains ancestry node indices, null if ancestry should be recomputed from scratch, defaults to [DEFAULT_INDEX_NS].
     */
    abstract val historyIndexNamespace: Property<String?>

    init {
        outputDirectory.convention(project.layout.buildDirectory.dir("takenaka/output"))
        cacheDirectory.convention(project.layout.buildDirectory.dir("takenaka/cache"))
        languageFlavor.convention(LanguageFlavor.JAVA)
        accessorFlavor.convention(AccessorFlavor.NONE)
        historyNamespaces.convention(listOf("mojang", "spigot", "searge", "intermediary"))
        historyIndexNamespace.convention(DEFAULT_INDEX_NS)
        relaxedCache.convention(true)
    }

    /**
     * Adds new versions to the [versions] property.
     *
     * @param versions the versions
     */
    fun versions(vararg versions: String) {
        this.versions.addAll(*versions)
    }

    /**
     * Adds new release versions to the [versions] property.
     *
     * @param older the older version range bound (inclusive)
     * @param newer the newer version range bound (inclusive)
     */
    fun versionRange(older: String, newer: String) {
        versionRange(older, newer) {
            includeTypes(Version.Type.RELEASE)
        }
    }

    /**
     * Adds new versions to the [versions] property.
     *
     * @param older the older version range bound (inclusive)
     * @param newer the newer version range bound (inclusive), defaults to the newest if null
     * @param block the version range configurator
     */
    @JvmOverloads
    fun versionRange(older: String, newer: String? = null, block: Action<VersionRangeBuilder>) {
        this.versions.addAll(VersionRangeBuilder(manifest, older, newer).apply(block::execute).toVersionList().map(Version::id))
    }

    /**
     * Sets the [outputDirectory] property.
     *
     * @param outputDirectory the file object, interpreted with [Project.file]
     */
    fun outputDirectory(outputDirectory: Any) {
        this.outputDirectory.set(project.file(outputDirectory))
    }

    /**
     * Sets the [cacheDirectory] property.
     *
     * @param cacheDirectory the file object, interpreted with [Project.file]
     */
    fun cacheDirectory(cacheDirectory: Any) {
        this.cacheDirectory.set(project.file(cacheDirectory))
    }

    /**
     * Sets the [relaxedCache] property.
     *
     * @param relaxedCache the relaxed cache flag
     */
    fun relaxedCache(relaxedCache: Boolean) {
        this.relaxedCache.set(relaxedCache)
    }

    /**
     * Sets the [basePackage] property.
     *
     * @param basePackage the base package
     */
    fun basePackage(basePackage: String) {
        this.basePackage.set(basePackage)
    }

    /**
     * Sets the [languageFlavor] property.
     *
     * @param languageFlavor the language flavor
     */
    fun languageFlavor(languageFlavor: LanguageFlavor) {
        this.languageFlavor.set(languageFlavor)
    }

    /**
     * Sets the [languageFlavor] property.
     *
     * @param languageFlavor the language flavor as a string
     */
    fun languageFlavor(languageFlavor: String) {
        this.languageFlavor.set(LanguageFlavor.valueOf(languageFlavor.uppercase()))
    }

    /**
     * Sets the [accessorFlavor] property.
     *
     * @param accessorFlavor the accessor flavor
     */
    fun accessorFlavor(accessorFlavor: AccessorFlavor) {
        this.accessorFlavor.set(accessorFlavor)
    }

    /**
     * Sets the [accessorFlavor] property.
     *
     * @param accessorFlavor the accessor flavor as a string
     */
    fun accessorFlavor(accessorFlavor: String) {
        this.accessorFlavor.set(AccessorFlavor.valueOf(accessorFlavor.uppercase()))
    }

    /**
     * Adds new namespaces to the [accessedNamespaces] property.
     *
     * @param accessedNamespaces the namespaces
     */
    fun accessedNamespaces(vararg accessedNamespaces: String) {
        this.accessedNamespaces.addAll(*accessedNamespaces)
    }

    /**
     * Sets the [historyNamespaces] property.
     *
     * @param historyNamespaces the history namespaces
     */
    fun historyNamespaces(vararg historyNamespaces: String) {
        this.historyNamespaces.addAll(*historyNamespaces)
    }

    /**
     * Sets the [historyIndexNamespace] property.
     *
     * @param historyIndexNamespace the index namespace, can be null
     */
    fun historyIndexNamespace(historyIndexNamespace: String?) {
        this.historyIndexNamespace.set(historyIndexNamespace)
    }

    /**
     * Creates a new accessor model with the supplied name.
     *
     * @param name the mapped class name or a glob pattern
     * @return the mapped class name ([name]), use this to refer to this class elsewhere
     */
    fun mapClass(name: String): String = mapClass(name) {}

    /**
     * Creates a new accessor model with the supplied name.
     *
     * @param name the mapped class name or a glob pattern
     * @param block the builder action
     * @return the mapped class name ([name]), use this to refer to this class elsewhere
     */
    fun mapClass(name: String, block: Action<ClassAccessorBuilder>): String {
        accessors.add(ClassAccessorBuilder(name, manifest).apply(block::execute).toClassAccessor())
        return name
    }
}

/**
 * A builder for [ClassAccessor].
 *
 * @property name mapped name of the accessed class
 * @property manifest the Mojang version manifest
 * @author Matouš Kučera
 */
class ClassAccessorBuilder(val name: String, internal val manifest: VersionManifest) {
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
        fields += FieldAccessor(name, null, version?.let { manifest[it] ?: error("Version $it not found in manifest") })
    }

    /**
     * Adds a new chained field accessor model.
     *
     * @param block the builder action
     */
    fun fieldChain(block: Action<FieldChainBuilder>) {
        fields += FieldChainBuilder(manifest).apply(block::execute).toFieldAccessor()
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
        fieldChain {
            names.forEach { name ->
                item(this@ClassAccessorBuilder.name, name)
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
            version?.let {
                requireNotNull(manifest[it]) {
                    "Version $it not found in manifest"
                }
            }
        )
    }

    /**
     * Adds a new chained method accessor model.
     *
     * @param block the builder action
     */
    fun methodChain(block: Action<MethodChainBuilder>) {
        methods += MethodChainBuilder(manifest).apply(block::execute).toMethodAccessor()
    }

    /**
     * Adds a new getter method accessor model (`{type} get{name}()` descriptor).
     *
     * **This method does not account for boolean-abbreviated getters (`isSomething`).**
     *
     * @param name the mapped method name
     * @param version the version of the [name] declaration, latest if null
     */
    @JvmOverloads
    fun getterInferred(name: String, version: String? = null) {
        methodInferred("get${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}", version)
    }

    /**
     * Adds a new getter method accessor model (`{type} get{name}()` descriptor).
     *
     * **This method does not account for boolean-abbreviated getters (`isSomething`).**
     *
     * **The type and name must both be mapped by the same namespace, else generation will fail!**
     * (e.g. both [type] and [name] must be Mojang names)
     *
     * @param type the mapped getter type, converted with [Any.asDescriptor]
     * @param name the mapped method name
     */
    fun getter(type: Any, name: String) {
        method(type, "get${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}")
    }

    /**
     * Adds a new chained getter method accessor model, useful for chaining together normal and record getters.
     *
     * **This method does not account for boolean-abbreviated getters (`isSomething`).**
     *
     * @param name the mapped method name
     * @param version the version of the [name] declaration, latest if null
     */
    @JvmOverloads
    fun getterChainInferred(name: String, version: String? = null) {
        methodChain {
            itemInferred(name, version)
            itemInferred("get${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}", version)
        }
    }

    /**
     * Adds a new chained getter method accessor model, useful for chaining together normal and record getters.
     *
     * **This method does not account for boolean-abbreviated getters (`isSomething`).**
     *
     * **The type and name must both be mapped by the same namespace, else generation will fail!**
     * (e.g. both [type] and [name] must be Mojang names)
     *
     * @param type the mapped getter type, converted with [Any.asDescriptor]
     * @param name the mapped method name
     */
    fun getterChain(type: Any, name: String) {
        methodChain {
            item(type, name)
            item(type, "get${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}")
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
        methodChain {
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
    internal fun toClassAccessor() = ClassAccessor(name, fields, constructors, methods, requiredMemberTypes)
}

/**
 * A single chain step.
 */
typealias ChainStep<T> = (T?) -> T

/**
 * A builder for field mapping chains.
 *
 * @property manifest the Mojang version manifest
 * @author Matouš Kučera
 */
class FieldChainBuilder(internal val manifest: VersionManifest) {
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
        steps += { last -> FieldAccessor(name, null, version?.let { manifest[it] ?: error("Version $it not found in manifest") }, chain = last) }
    }

    /**
     * Creates a [FieldAccessor] out of this builder.
     *
     * @return the field accessor
     */
    internal fun toFieldAccessor(): FieldAccessor {
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
 * A builder for method mapping chains.
 *
 * @property manifest the Mojang version manifest
 * @author Matouš Kučera
 */
class MethodChainBuilder(internal val manifest: VersionManifest) {
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
               version?.let {
                   requireNotNull(manifest[it]) {
                       "Version $it not found in manifest"
                   }
               },
               chain = last
           )
       }
    }

    /**
     * Creates a [MethodAccessor] out of this builder.
     *
     * @return the method accessor
     */
    internal fun toMethodAccessor(): MethodAccessor {
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
