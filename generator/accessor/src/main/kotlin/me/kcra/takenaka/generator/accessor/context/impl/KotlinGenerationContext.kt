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
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.AccessorType
import me.kcra.takenaka.generator.accessor.util.escapeKotlinName
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*

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
        val accessorSimpleName = generateNonConflictingName(resolvedAccessor.model.internalName).escapeKotlinName()

        val typeSpecs = buildList<Any> {
            val mappingClassName = KClassName(generator.config.basePackage, "${accessorSimpleName}Mapping")
            val accessorClassName = KClassName(generator.config.basePackage, "${accessorSimpleName}Accessor")
            val accessorBuilder = KTypeSpec.objectBuilder(accessorClassName)
                .addKdoc(
                    """
                        Accessors for the `%L` class.
                        
                        @see %L
                    """.trimIndent(),
                    accessedQualifiedName,
                    mappingClassName.canonicalName
                )
                .addProperty(
                    PropertySpec.builder("TYPE", SourceTypes.KT_NULLABLE_CLASS_WILDCARD)
                        .delegate("%M(%T::getClazz)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                        .build()
                )

            add(
                KTypeSpec.objectBuilder(mappingClassName)
                    .addKdoc(
                        """
                            Mappings for the `%L` class.
                            
                            `%L` - `%L`
                        """.trimIndent(),
                        accessedQualifiedName,
                        resolvedAccessor.node.last.key.id,
                        resolvedAccessor.node.first.key.id
                    )
                    .superclass(SourceTypes.KT_CLASS_MAPPING)
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
                                beginControlFlow("%M(%S)", SourceTypes.KT_CLASS_MAPPING_FIELD_DSL, fieldAccessor.name)

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
                                beginControlFlow("%M", SourceTypes.KT_CLASS_MAPPING_CTOR_DSL)

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
                                beginControlFlow("%M(%S)", SourceTypes.KT_CLASS_MAPPING_METHOD_DSL, methodAccessor.name)

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
                        resolvedAccessor.fields.map { (fieldAccessor, fieldNode) ->
                            val overloadIndex = resolvedAccessor.fieldOverloads[fieldAccessor]
                            val accessorName = "FIELD_${fieldAccessor.upperName.escapeKotlinName()}${overloadIndex?.let { if (it != 0) "_$it" else "" } ?: ""}"
                            val fieldType = fieldAccessor.type?.let(Type::getType)
                                ?: getFriendlyType(fieldNode.last.value)

                            fun PropertySpec.Builder.addMeta(constant: Boolean = false): PropertySpec.Builder = apply {
                                addKdoc(
                                    """
                                        Accessor for the `%L %L` %L.
                                        
                                        @see %L.%L
                                    """.trimIndent(),
                                    fieldType.className,
                                    fieldAccessor.name,
                                    if (constant) {
                                        "constant field value"
                                    } else {
                                        "field"
                                    },
                                    mappingClassName.canonicalName,
                                    accessorName
                                )
                            }

                            val mod = fieldNode.last.value.modifiers
                            if ((mod and Opcodes.ACC_STATIC) != 0 && (mod and Opcodes.ACC_FINAL) != 0) { // constant
                                accessorBuilder.addProperty(
                                    PropertySpec.builder(accessorName, SourceTypes.KT_NULLABLE_ANY)
                                        .addMeta(constant = true)
                                        .delegate("%M(%T.$accessorName::getConstantValue)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                        .build()
                                )
                            } else {
                                when (generator.config.accessorType) {
                                    AccessorType.REFLECTION -> {
                                        accessorBuilder.addProperty(
                                            PropertySpec.builder(accessorName, SourceTypes.KT_NULLABLE_FIELD)
                                                .addMeta()
                                                .delegate("%M(%T.$accessorName::getField)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                                .build()
                                        )
                                    }
                                    AccessorType.METHOD_HANDLES -> {
                                        accessorBuilder.addProperty(
                                            PropertySpec.builder("${accessorName}_GETTER", SourceTypes.KT_NULLABLE_METHOD_HANDLE)
                                                .addMeta()
                                                .delegate("%M(%T.$accessorName::getFieldGetter)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                                .build()
                                        )
                                        accessorBuilder.addProperty(
                                            PropertySpec.builder("${accessorName}_SETTER", SourceTypes.KT_NULLABLE_METHOD_HANDLE)
                                                .addMeta()
                                                .delegate("%M(%T.$accessorName::getFieldSetter)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                                .build()
                                        )
                                    }
                                    AccessorType.NONE -> {}
                                }
                            }

                            PropertySpec.builder(accessorName, SourceTypes.KT_FIELD_MAPPING)
                                .addKdoc(
                                    """
                                        Mapping for the `%L %L` field.
                                        
                                        `%L` - `%L`
                                    """.trimIndent(),
                                    fieldType.className,
                                    fieldAccessor.name,
                                    fieldNode.first.key.id,
                                    fieldNode.last.key.id
                                )
                                .initializer {
                                    add("getField(%S, %L)!!", fieldAccessor.name, overloadIndex)
                                    if (fieldAccessor.chain != null) {
                                        add(
                                            ".chain(FIELD_%L%L)",
                                            fieldAccessor.chain.upperName.escapeKotlinName(),
                                            resolvedAccessor.fieldOverloads[fieldAccessor.chain]
                                                ?.let { if (it != 0) "_$it" else "" }
                                                ?: ""
                                        )
                                    }
                                }
                                .build()
                        }
                    )
                    .addProperties(
                        resolvedAccessor.constructors.mapIndexed { i, (ctorAccessor, ctorNode) ->
                            val accessorName = "CONSTRUCTOR_$i"
                            val ctorArgs = Type.getArgumentTypes(ctorAccessor.type)

                            fun PropertySpec.Builder.addMeta(): PropertySpec.Builder = apply {
                                addKdoc(
                                    """
                                        Accessor for the `(%L)` constructor.
                                        
                                        @see %L.%L
                                    """.trimIndent(),
                                    ctorArgs.joinToString(transform = Type::getClassName),
                                    mappingClassName.canonicalName,
                                    accessorName
                                )
                            }

                            when (generator.config.accessorType) {
                                AccessorType.REFLECTION -> {
                                    accessorBuilder.addProperty(
                                        PropertySpec.builder(accessorName, SourceTypes.KT_NULLABLE_CONSTRUCTOR_WILDCARD)
                                            .addMeta()
                                            .delegate("%M(%T.$accessorName::getConstructor)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                            .build()
                                    )
                                }
                                AccessorType.METHOD_HANDLES -> {
                                    accessorBuilder.addProperty(
                                        PropertySpec.builder(accessorName, SourceTypes.KT_NULLABLE_METHOD_HANDLE)
                                            .addMeta()
                                            .delegate("%M(%T.$accessorName::getConstructorHandle)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                            .build()
                                    )
                                }
                                AccessorType.NONE -> {}
                            }

                            PropertySpec.builder(accessorName, SourceTypes.KT_CONSTRUCTOR_MAPPING)
                                .addKdoc(
                                    """
                                        Mapping for the `(%L)` constructor.
                                        
                                        `%L` - `%L`
                                    """.trimIndent(),
                                    ctorArgs.joinToString(transform = Type::getClassName),
                                    ctorNode.first.key.id,
                                    ctorNode.last.key.id
                                )
                                .initializer("getConstructor(%L)!!", i)
                                .build()
                        }
                    )
                    .addProperties(
                        resolvedAccessor.methods.map { (methodAccessor, methodNode) ->
                            val overloadIndex = resolvedAccessor.methodOverloads[methodAccessor]
                            val accessorName = "METHOD_${methodAccessor.upperName.escapeKotlinName()}${overloadIndex?.let { if (it != 0) "_$it" else "" } ?: ""}"
                            val methodType = if (methodAccessor.isIncomplete) {
                                getFriendlyType(methodNode.last.value)
                            } else {
                                Type.getType(methodAccessor.type)
                            }

                            fun PropertySpec.Builder.addMeta(): PropertySpec.Builder = apply {
                                addKdoc(
                                    """
                                        Accessor for the `%L %L(%L)` method.
                                        
                                        @see %L.%L
                                    """.trimIndent(),
                                    methodType.returnType.className,
                                    methodAccessor.name,
                                    methodType.argumentTypes.joinToString(transform = Type::getClassName),
                                    mappingClassName.canonicalName,
                                    accessorName
                                )
                            }

                            when (generator.config.accessorType) {
                                AccessorType.REFLECTION -> {
                                    accessorBuilder.addProperty(
                                        PropertySpec.builder(accessorName, SourceTypes.KT_NULLABLE_METHOD)
                                            .addMeta()
                                            .delegate("%M(%T.$accessorName::getMethod)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                            .build()
                                    )
                                }
                                AccessorType.METHOD_HANDLES -> {
                                    accessorBuilder.addProperty(
                                        PropertySpec.builder(accessorName, SourceTypes.KT_NULLABLE_METHOD_HANDLE)
                                            .addMeta()
                                            .delegate("%M(%T.$accessorName::getMethodHandle)", SourceTypes.KT_LAZY_DSL, mappingClassName)
                                            .build()
                                    )
                                }
                                AccessorType.NONE -> {}
                            }

                            PropertySpec.builder(accessorName, SourceTypes.KT_METHOD_MAPPING)
                                .addKdoc(
                                    """
                                        Mapping for the `%L %L(%L)` method.
                                        
                                        `%L` - `%L`
                                    """.trimIndent(),
                                    methodType.returnType.className,
                                    methodAccessor.name,
                                    methodType.argumentTypes.joinToString(transform = Type::getClassName),
                                    methodNode.first.key.id,
                                    methodNode.last.key.id
                                )
                                .initializer {
                                    add("getMethod(%S, %L)!!", methodAccessor.name, overloadIndex)
                                    if (methodAccessor.chain != null) {
                                        add(
                                            ".chain(METHOD_%L%L)",
                                            methodAccessor.chain.upperName.escapeKotlinName(),
                                            resolvedAccessor.methodOverloads[methodAccessor.chain]
                                                ?.let { if (it != 0) "_$it" else "" }
                                                ?: ""
                                        )
                                    }
                                }
                                .build()
                        }
                    )
                    .build()
            )

            if (generator.config.accessorType != AccessorType.NONE) {
                add(accessorBuilder.build())
            }
        }

        typeSpecs.writeTo(
            generator.workspace,
            accessorSimpleName.replaceFirstChar { it.lowercase(Locale.getDefault()) },
            // import LazySupplier delegate for accessors
            includeMappingDsl = generator.config.accessorType != AccessorType.NONE
        )
    }

    /**
     * Generates a Kotlin mapping lookup class from class names.
     *
     * @param names internal names of classes declared in accessor models
     */
    override fun generateLookupClass(names: List<String>) {
        PropertySpec.builder("LOOKUP", SourceTypes.KT_MAPPING_LOOKUP)
            .addKdoc("Mapping lookup index.")
            .initializer {
                beginControlFlow("%M", SourceTypes.KT_MAPPING_LOOKUP_DSL)

                names.forEach { name ->
                    val mappingClassName = KClassName(
                        generator.config.basePackage,
                        "${name.substringAfterLast('/').escapeKotlinName()}Mapping"
                    )

                    addStatement("put(%T)", mappingClassName)
                }

                endControlFlow()
            }
            .build()
            .writeTo(generator.workspace, "lookup")
    }

    /**
     * Writes a [PropertySpec] to a workspace with default settings.
     *
     * @param workspace the workspace
     * @param name the file name
     * @param includeMappingDsl whether imports for implicit mapping DSL should be added
     */
    fun PropertySpec.writeTo(
        workspace: Workspace,
        name: String = requireNotNull(this.name) { "File name required, but type has no name" },
        includeMappingDsl: Boolean = false
    ) {
        listOf(this).writeTo(workspace, name, includeMappingDsl)
    }

    /**
     * Writes KotlinPoet elements ([KTypeSpec], [FunSpec], [PropertySpec], [TypeAliasSpec]) to a single file in a workspace with default settings.
     *
     * @param workspace the workspace
     * @param name the file name
     * @param includeMappingDsl whether imports for implicit mapping DSL should be added
     */
    fun Iterable<Any>.writeTo(workspace: Workspace, name: String, includeMappingDsl: Boolean = false) {
        FileSpec.builder(generator.config.basePackage, name)
            .addFileComment("This file was generated by takenaka on ${DATE_FORMAT.format(generationTime)}. Do not edit, changes will be overwritten!")
            .addKotlinDefaultImports(includeJvm = true)
            .indent(" ".repeat(4)) // 4 spaces
            .apply {
                if (includeMappingDsl) {
                    addImport(SourceTypes.KT_LAZY_DELEGATE_DSL)
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
