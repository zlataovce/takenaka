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

@file:OptIn(KotlinPoetJavaPoetPreview::class)

package me.kcra.takenaka.generator.accessor.context.impl

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.javapoet.KClassName
import com.squareup.kotlinpoet.javapoet.KTypeSpec
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.core.mapping.toInternalName
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.AccessorType
import me.kcra.takenaka.generator.accessor.GeneratedClassType
import me.kcra.takenaka.generator.accessor.GeneratedMemberType
import me.kcra.takenaka.generator.accessor.util.escapeKotlinName
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * A generation context that emits Kotlin code.
 *
 * @param generator the generator
 * @param ancestryProvider the ancestryProvider
 * @param contextScope the coroutine scope of this context
 * @author Matouš Kučera
 */
open class KotlinGenerationContext(
    generator: AccessorGenerator,
    ancestryProvider: AncestryProvider,
    contextScope: CoroutineScope
) : AbstractGenerationContext(generator, ancestryProvider, contextScope) {
    /**
     * Generates an accessor class from a model in Kotlin.
     *
     * @param resolvedAccessor the accessor model
     */
    override fun generateClass(resolvedAccessor: ResolvedClassAccessor) {
        val accessedQualifiedName = resolvedAccessor.model.name.fromInternalName()

        val mappingClassName = namingStrategy.klass(resolvedAccessor.model, GeneratedClassType.MAPPING).escapeKotlinName().toKClassName()
        val accessorClassName = namingStrategy.klass(resolvedAccessor.model, GeneratedClassType.ACCESSOR).escapeKotlinName().toKClassName()
        val accessorBuilder = KTypeSpec.objectBuilder(accessorClassName)
            .addKdoc(
                """
                    Accessors for the `%L` class.
                    
                    `%L` - `%L`
                """.trimIndent(),
                accessedQualifiedName,
                resolvedAccessor.node.first.key.id,
                resolvedAccessor.node.last.key.id
            )
            .addProperty(
                PropertySpec.builder(namingStrategy.member(GeneratedMemberType.TYPE), types.KT_NULLABLE_CLASS_WILDCARD)
                    .delegate("%M(%T::getClazz)", types.KT_LAZY_DSL, mappingClassName)
                    .build()
            )

        if (generator.config.mappingWebsite != null) {
            val link = "${generator.config.mappingWebsite}/${resolvedAccessor.node.last.key.id}/${getFriendlyName(resolvedAccessor.node.last.value).toInternalName()}.html"
            accessorBuilder.addKdoc("\nSee: [%L](%L)", link, link)
        }

        accessorBuilder.addKdoc("\n@see·%L", mappingClassName.canonicalName)

        KTypeSpec.objectBuilder(mappingClassName)
            .addKdoc(
                """
                    Mappings for the `%L` class.
                    
                    `%L` - `%L`
                """.trimIndent(),
                accessedQualifiedName,
                resolvedAccessor.node.first.key.id,
                resolvedAccessor.node.last.key.id
            )
            .superclass(types.KT_CLASS_MAPPING)
            .addSuperclassConstructorParameter("%S", accessedQualifiedName)
            .addInitializerBlock(
                buildCodeBlock {
                    groupClassNames(resolvedAccessor.node).forEach { (classKey, versions) ->
                        val (ns, name) = classKey

                        addStatement(
                            "put(%S, %S, %L)",
                            ns,
                            name,
                            versions.map { KCodeBlock.of("%S", it.id) }.joinToCode()
                        )
                    }

                    resolvedAccessor.fields.forEach { (fieldAccessor, fieldNode) ->
                        beginControlFlow("%M(%S)", types.KT_CLASS_MAPPING_FIELD_DSL, fieldAccessor.name)

                        groupFieldNames(fieldNode).forEach { (fieldKey, versions) ->
                            val (ns, name) = fieldKey

                            addStatement(
                                "put(%S, %S, %L)",
                                ns,
                                name,
                                versions.map { KCodeBlock.of("%S", it.id) }.joinToCode()
                            )
                        }

                        endControlFlow()
                    }

                    resolvedAccessor.constructors.forEach { (_, ctorNode) ->
                        beginControlFlow("%M", types.KT_CLASS_MAPPING_CTOR_DSL)

                        groupConstructorNames(ctorNode).forEach { (ctorKey, versions) ->
                            val (ns, desc) = ctorKey

                            val args = Type.getArgumentTypes(desc)
                            addStatement(
                                "put(%S, arrayOf(%L)%L)",
                                ns,
                                versions.map { KCodeBlock.of("%S", it.id) }.joinToCode(),
                                if (args.isEmpty()) "" else args.map { KCodeBlock.of("%S", it.className) }.joinToCode(prefix = ", ")
                            )
                        }

                        endControlFlow()
                    }

                    resolvedAccessor.methods.forEach { (methodAccessor, methodNode) ->
                        beginControlFlow("%M(%S)", types.KT_CLASS_MAPPING_METHOD_DSL, methodAccessor.name)

                        groupMethodNames(methodNode).forEach { (methodKey, versions) ->
                            val (ns, name, desc) = methodKey

                            val args = Type.getArgumentTypes(desc)
                            addStatement(
                                "put(%S, arrayOf(%L), %S%L)",
                                ns,
                                versions.map { KCodeBlock.of("%S", it.id) }.joinToCode(),
                                name,
                                if (args.isEmpty()) "" else args.map { KCodeBlock.of("%S", it.className) }.joinToCode(prefix = ", ")
                            )
                        }

                        endControlFlow()
                    }
                }
            )
            .addProperties(
                resolvedAccessor.fields.map { mergedAccessor ->
                    val (fieldAccessor, fieldNode, overloadIndex) = mergedAccessor

                    val mappingName = namingStrategy.field(fieldAccessor, overloadIndex)
                    fun PropertySpec.Builder.addMeta(constant: Boolean = false, mapping: Boolean = false): PropertySpec.Builder = apply {
                        if (mergedAccessor.isChained) {
                            addKdoc(
                                "%L for the following %L:\n",
                                if (mapping) "Mapping" else "Accessor",
                                if (constant) "constant field values" else "fields"
                            )

                            mergedAccessor.forEach { chainedAccessor, chainedFieldNode ->
                                val chainedType = chainedAccessor.type?.let(Type::getType)
                                    ?: getFriendlyType(chainedFieldNode.last.value)

                                addKdoc(
                                    " - `%L %L` (`%L` - `%L`)\n",
                                    chainedType.className,
                                    chainedAccessor.name,
                                    chainedFieldNode.first.key.id,
                                    chainedFieldNode.last.key.id,
                                )
                            }
                        } else {
                            val fieldType = fieldAccessor.type?.let(Type::getType)
                                ?: getFriendlyType(fieldNode.last.value)

                            addKdoc(
                                "%L for the `%L %L` %L.\n",
                                if (mapping) "Mapping" else "Accessor",
                                fieldType.className,
                                fieldAccessor.name,
                                if (constant) "constant field value" else "field"
                            )
                        }

                        addKdoc("\n`%L` - `%L`", fieldNode.first.key.id, fieldNode.last.key.id)
                        if (!mapping) {
                            addKdoc("\n@see·%L.%L", mappingClassName.canonicalName, mappingName)
                        }
                    }

                    val mod = fieldNode.last.value.modifiers
                    if ((mod and Opcodes.ACC_STATIC) != 0 && (mod and Opcodes.ACC_FINAL) != 0) { // constant
                        accessorBuilder.addProperty(
                            PropertySpec.builder(namingStrategy.constant(fieldAccessor, overloadIndex), types.KT_NULLABLE_ANY)
                                .addMeta(constant = true)
                                .delegate("%M(%T.%L::getConstantValue)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                .build()
                        )
                    } else {
                        when (generator.config.accessorType) {
                            AccessorType.REFLECTION -> {
                                accessorBuilder.addProperty(
                                    PropertySpec.builder(mappingName, types.KT_NULLABLE_FIELD)
                                        .addMeta()
                                        .delegate("%M(%T.%L::getField)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                        .build()
                                )
                            }
                            AccessorType.METHOD_HANDLES -> {
                                accessorBuilder.addProperty(
                                    PropertySpec.builder(namingStrategy.fieldHandle(fieldAccessor, overloadIndex, false), types.KT_NULLABLE_METHOD_HANDLE)
                                        .addMeta()
                                        .delegate("%M(%T.%L::getFieldGetter)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                        .build()
                                )
                                accessorBuilder.addProperty(
                                    PropertySpec.builder(namingStrategy.fieldHandle(fieldAccessor, overloadIndex, true), types.KT_NULLABLE_METHOD_HANDLE)
                                        .addMeta()
                                        .delegate("%M(%T.%L::getFieldSetter)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                        .build()
                                )
                            }
                            AccessorType.NONE -> {}
                        }
                    }

                    PropertySpec.builder(mappingName, types.KT_FIELD_MAPPING)
                        .addMeta(mapping = true)
                        .initializer("getField(%S, %L)!!", fieldAccessor.name, overloadIndex)
                        .build()
                }
            )
            .addProperties(
                resolvedAccessor.constructors.map { (ctorAccessor, ctorNode, overloadIndex) ->
                    val mappingName = namingStrategy.constructor(ctorAccessor, overloadIndex)
                    val ctorArgs = Type.getArgumentTypes(ctorAccessor.type)

                    fun PropertySpec.Builder.addMeta(): PropertySpec.Builder = apply {
                        addKdoc(
                            """
                                Accessor for the `(%L)` constructor.
                                
                                `%L` - `%L`
                                @see·%L.%L
                            """.trimIndent(),
                            ctorArgs.joinToString(transform = Type::getClassName),
                            ctorNode.first.key.id,
                            ctorNode.last.key.id,
                            mappingClassName.canonicalName,
                            mappingName
                        )
                    }

                    when (generator.config.accessorType) {
                        AccessorType.REFLECTION -> {
                            accessorBuilder.addProperty(
                                PropertySpec.builder(mappingName, types.KT_NULLABLE_CONSTRUCTOR_WILDCARD)
                                    .addMeta()
                                    .delegate("%M(%T.%L::getConstructor)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.METHOD_HANDLES -> {
                            accessorBuilder.addProperty(
                                PropertySpec.builder(mappingName, types.KT_NULLABLE_METHOD_HANDLE)
                                    .addMeta()
                                    .delegate("%M(%T.%L::getConstructorHandle)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.NONE -> {}
                    }

                    PropertySpec.builder(mappingName, types.KT_CONSTRUCTOR_MAPPING)
                        .addKdoc(
                            """
                                Mapping for the `(%L)` constructor.
                                
                                `%L` - `%L`
                            """.trimIndent(),
                            ctorArgs.joinToString(transform = Type::getClassName),
                            ctorNode.first.key.id,
                            ctorNode.last.key.id
                        )
                        .initializer("getConstructor(%L)!!", overloadIndex)
                        .build()
                }
            )
            .addProperties(
                resolvedAccessor.methods.map { mergedAccessor ->
                    val (methodAccessor, methodNode, overloadIndex) = mergedAccessor

                    val mappingName = namingStrategy.method(methodAccessor, overloadIndex)
                    fun PropertySpec.Builder.addMeta(mapping: Boolean = false): PropertySpec.Builder = apply {
                        if (mergedAccessor.isChained) {
                            addKdoc(
                                "%L for the following methods:\n",
                                if (mapping) "Mapping" else "Accessor"
                            )

                            mergedAccessor.forEach { (chainedAccessor, chainedMethodNode) ->
                                val chainedType = if (chainedAccessor.isIncomplete) getFriendlyType(chainedMethodNode.last.value) else Type.getType(chainedAccessor.type)

                                addKdoc(
                                    " - `%L %L(%L)` (`%L` - `%L`)\n",
                                    chainedType.returnType.className,
                                    chainedAccessor.name,
                                    chainedType.argumentTypes.joinToString(transform = Type::getClassName),
                                    chainedMethodNode.first.key.id,
                                    chainedMethodNode.last.key.id
                                )
                            }
                        } else {
                            val methodType = if (methodAccessor.isIncomplete) getFriendlyType(methodNode.last.value) else Type.getType(methodAccessor.type)

                            addKdoc(
                                "%L for the `%L %L(%L)` method.\n",
                                if (mapping) "Mapping" else "Accessor",
                                methodType.returnType.className,
                                methodAccessor.name,
                                methodType.argumentTypes.joinToString(transform = Type::getClassName)
                            )
                        }

                        addKdoc("\n`%L` - `%L`", methodNode.first.key.id, methodNode.last.key.id)
                        if (!mapping) {
                            addKdoc("\n@see·%L.%L", mappingClassName.canonicalName, mappingName)
                        }
                    }

                    when (generator.config.accessorType) {
                        AccessorType.REFLECTION -> {
                            accessorBuilder.addProperty(
                                PropertySpec.builder(mappingName, types.KT_NULLABLE_METHOD)
                                    .addMeta()
                                    .delegate("%M(%T.%L::getMethod)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.METHOD_HANDLES -> {
                            accessorBuilder.addProperty(
                                PropertySpec.builder(mappingName, types.KT_NULLABLE_METHOD_HANDLE)
                                    .addMeta()
                                    .delegate("%M(%T.%L::getMethodHandle)", types.KT_LAZY_DSL, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.NONE -> {}
                    }

                    PropertySpec.builder(mappingName, types.KT_METHOD_MAPPING)
                        .addMeta(mapping = true)
                        .initializer("getMethod(%S, %L)!!", methodAccessor.name, overloadIndex)
                        .build()
                }
            )
            .build()
            .writeTo(mappingClassName, generator.workspace)

        if (generator.config.accessorType != AccessorType.NONE) {
            accessorBuilder.build()
                .writeTo(accessorClassName, generator.workspace, includeMappingDsl = true)
        }
    }

    /**
     * Generates a Kotlin mapping lookup class from class names.
     *
     * @param names fully qualified names of generated mapping classes
     */
    override fun generateLookupClass(names: List<String>) {
        PropertySpec.builder(namingStrategy.member(GeneratedMemberType.LOOKUP), types.KT_MAPPING_LOOKUP)
            .addKdoc("Mapping lookup index.")
            .initializer {
                beginControlFlow("%M", types.KT_MAPPING_LOOKUP_DSL)

                names.forEach { name ->
                    addStatement("put(%T)", name.escapeKotlinName().toKClassName())
                }

                endControlFlow()
            }
            .build()
            .writeTo(namingStrategy.klass(GeneratedClassType.LOOKUP).toKClassName(), generator.workspace)
    }

    /**
     * Writes a [PropertySpec] to a workspace with default settings.
     *
     * @param name the file name
     * @param workspace the workspace
     * @param includeMappingDsl whether imports for implicit mapping DSL should be added
     */
    fun PropertySpec.writeTo(
        name: KClassName,
        workspace: Workspace,
        includeMappingDsl: Boolean = false
    ) {
        listOf(this).writeTo(name, workspace, includeMappingDsl)
    }

    /**
     * Writes a [KTypeSpec] to a workspace with default settings.
     *
     * @param name the file name
     * @param workspace the workspace
     * @param includeMappingDsl whether imports for implicit mapping DSL should be added
     */
    fun KTypeSpec.writeTo(
        name: KClassName,
        workspace: Workspace,
        includeMappingDsl: Boolean = false
    ) {
        listOf(this).writeTo(name, workspace, includeMappingDsl)
    }

    /**
     * Writes KotlinPoet elements ([KTypeSpec], [FunSpec], [PropertySpec], [TypeAliasSpec]) to a single file in a workspace with default settings.
     *
     * @param workspace the workspace
     * @param name the file name
     * @param includeMappingDsl whether imports for implicit mapping DSL should be added
     */
    fun Iterable<Any>.writeTo(name: KClassName, workspace: Workspace, includeMappingDsl: Boolean = false) {
        FileSpec.builder(name.packageName, name.simpleName)
            .addFileComment("This file was generated by takenaka on ${DATE_FORMAT.format(generationTime)}. Do not edit, changes will be overwritten!")
            .addKotlinDefaultImports(includeJvm = true)
            .indent(" ".repeat(4)) // 4 spaces
            .apply {
                if (includeMappingDsl) {
                    addImport(types.KT_LAZY_DELEGATE_DSL)
                }

                forEach { elem ->
                    when (elem) {
                        is KTypeSpec -> addType(elem)
                        is FunSpec -> addFunction(elem)
                        is PropertySpec -> addProperty(elem)
                        is TypeAliasSpec -> addTypeAlias(elem)
                        else -> throw UnsupportedOperationException("${elem::class.qualifiedName} is not a recognized KotlinPoet element")
                    }
                }
            }
            .build()
            .writeTo(workspace)
    }
}

/**
 * Writes a [FileSpec] to a workspace.
 *
 * @param workspace the workspace
 */
fun FileSpec.writeTo(workspace: Workspace) = writeTo(workspace.rootDirectory)
