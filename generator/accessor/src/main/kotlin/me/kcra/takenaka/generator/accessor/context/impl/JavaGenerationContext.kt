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

import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.kotlinpoet.javapoet.JClassName
import com.squareup.kotlinpoet.javapoet.JParameterizedTypeName
import com.squareup.kotlinpoet.javapoet.JTypeSpec
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.AccessorType
import me.kcra.takenaka.generator.accessor.GeneratedClassType
import me.kcra.takenaka.generator.accessor.GeneratedMemberType
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.nio.file.Path
import javax.lang.model.element.Modifier

/**
 * A generation context that emits Java code.
 *
 * @param generator the generator
 * @param ancestryProvider the ancestry provider
 * @param contextScope the coroutine scope of this context
 * @author Matouš Kučera
 */
open class JavaGenerationContext(
    generator: AccessorGenerator,
    ancestryProvider: AncestryProvider,
    contextScope: CoroutineScope
) : AbstractGenerationContext(generator, ancestryProvider, contextScope) {
    /**
     * Generates an accessor class from a model in Java.
     *
     * @param resolvedAccessor the accessor model
     */
    override fun generateClass(resolvedAccessor: ResolvedClassAccessor) {
        val accessedQualifiedName = resolvedAccessor.model.name.fromInternalName()

        val mappingClassName = namingStrategy.klass(resolvedAccessor.model, GeneratedClassType.MAPPING).toJClassName()
        val accessorClassName = namingStrategy.klass(resolvedAccessor.model, GeneratedClassType.ACCESSOR).toJClassName()
        val mappingFieldName = namingStrategy.member(GeneratedMemberType.MAPPING)
        
        val accessorBuilder = JTypeSpec.interfaceBuilder(accessorClassName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                """
                    Accessors for the {@code ${'$'}L} class.
                    
                    @since ${'$'}L
                    @version ${'$'}L
                    @see ${'$'}L
                """.trimIndent(),
                accessedQualifiedName,
                resolvedAccessor.node.first.key.id,
                resolvedAccessor.node.last.key.id,
                mappingClassName.canonicalName()
            )
            .addField(
                FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.CLASS_WILDCARD), namingStrategy.member(GeneratedMemberType.TYPE), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(types.NOT_NULL)
                    .initializer("\$T.of(\$T.\$L::getClazz)", types.LAZY_SUPPLIER, mappingClassName, mappingFieldName)
                    .build()
            )

        JTypeSpec.interfaceBuilder(mappingClassName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                """
                    Mappings for the {@code ${'$'}L} class.
                    
                    @since ${'$'}L
                    @version ${'$'}L
                """.trimIndent(),
                accessedQualifiedName,
                resolvedAccessor.node.first.key.id,
                resolvedAccessor.node.last.key.id
            )
            .addField(
                FieldSpec.builder(types.CLASS_MAPPING, mappingFieldName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(types.NOT_NULL)
                    .initializer {
                        add("new \$T(\$S)", types.CLASS_MAPPING, accessedQualifiedName)
                        withIndent {
                            groupClassNames(resolvedAccessor.node).forEach { (classKey, versions) ->
                                val (ns, name) = classKey

                                add("\n.put(\$S, \$S, \$L)", ns, name, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "))
                            }

                            resolvedAccessor.fields.forEach { (fieldAccessor, fieldNode) ->
                                add("\n.putField(\$S)", fieldAccessor.name)
                                withIndent {
                                    groupFieldNames(fieldNode).forEach { (fieldKey, versions) ->
                                        val (ns, name) = fieldKey

                                        add("\n.put(\$S, \$S, \$L)", ns, name, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "))
                                    }

                                    add("\n.getParent()")
                                }
                            }

                            resolvedAccessor.constructors.forEach { (_, ctorNode) ->
                                add("\n.putConstructor()")
                                withIndent {
                                    groupConstructorNames(ctorNode).forEach { (ctorKey, versions) ->
                                        val (ns, desc) = ctorKey

                                        add("\n.put(\$S, new \$T[] { \$L }", ns, types.STRING, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "))

                                        val args = Type.getArgumentTypes(desc)
                                            .map { JCodeBlock.of("\$S", it.className) }

                                        if (args.isNotEmpty()) {
                                            add(", ")
                                            add(JCodeBlock.join(args, ", "))
                                        }

                                        add(")")
                                    }

                                    add("\n.getParent()")
                                }
                            }

                            resolvedAccessor.methods.forEach { (methodAccessor, methodNode) ->
                                add("\n.putMethod(\$S)", methodAccessor.name)
                                withIndent {
                                    groupMethodNames(methodNode).forEach { (methodKey, versions) ->
                                        val (ns, name, desc) = methodKey

                                        add("\n.put(\$S, new \$T[] { \$L }, \$S", ns, types.STRING, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "), name)

                                        val args = Type.getArgumentTypes(desc)
                                            .map { JCodeBlock.of("\$S", it.className) }

                                        if (args.isNotEmpty()) {
                                            add(", ")
                                            add(JCodeBlock.join(args, ", "))
                                        }

                                        add(")")
                                    }

                                    add("\n.getParent()")
                                }
                            }
                        }
                    }
                    .build()
            )
            .addFields(
                resolvedAccessor.fields.map { (fieldAccessor, fieldNode) ->
                    if (fieldAccessor.skipAccessorGeneration) {
                        return@map null
                    }

                    val overloadIndex = resolvedAccessor.fieldAccessorOverloads[fieldAccessor] ?: 0
                    val fieldType = fieldAccessor.type?.let(Type::getType)
                        ?: getFriendlyType(fieldNode.last.value)

                    val mappingName = namingStrategy.field(fieldAccessor, overloadIndex)

                    val chain = ArrayList<ResolvedFieldPair>()

                    val lowestVersion: Version
                    val highestVersion: Version

                    if (fieldAccessor.chain != null) {
                        var chainedAccessor: FieldAccessor? = fieldAccessor
                        while (chainedAccessor != null) {
                            val chainedFieldNode = resolvedAccessor.fields.find { it.first == chainedAccessor }?.second
                                ?: throw RuntimeException("Chained field node should not be null at this point")
                            chain += ResolvedFieldPair(chainedAccessor, chainedFieldNode)
                            chainedAccessor = chainedAccessor.chain
                        }

                        lowestVersion = chain.minOf { it.second.first.key }
                        highestVersion = chain.maxOf { it.second.last.key }
                    } else {
                        lowestVersion = fieldNode.first.key
                        highestVersion = fieldNode.last.key
                    }

                    fun FieldSpec.Builder.addMeta(constant: Boolean = false, mapping: Boolean = false): FieldSpec.Builder = apply {
                        addAnnotation(types.NOT_NULL)

                        if (chain.isNotEmpty()) {
                            addJavadoc(
                                """
                                ${'$'}L for the following ${'$'}L:
                                <ul>
                                     
                                """.trimIndent(),
                                if (mapping) {
                                    "Mapping"
                                } else {
                                    "Accessor"
                                },
                                if (constant) {
                                    "constant field values"
                                } else {
                                    "fields"
                                }
                            )

                            for ((chainedAccessor, chainedFieldNode) in chain) {
                                val chainedType = chainedAccessor.type?.let(Type::getType) ?: getFriendlyType(chainedFieldNode.last.value)
                                addJavadoc(
                                    """
                                    <li>{@code ${'$'}L ${'$'}L} (${'$'}L-${'$'}L)</li>
                                    
                                    """.trimIndent(),
                                    chainedType.className,
                                    chainedAccessor.name,
                                    chainedFieldNode.first.key.id,
                                    chainedFieldNode.last.key.id,
                                )
                            }

                            addJavadoc(
                                """
                                </ul>
                                
                                """.trimIndent()
                            )
                        } else {
                            addJavadoc(
                                """
                                ${'$'}L for the {@code ${'$'}L ${'$'}L} ${'$'}L.
                                
                                """.trimIndent(),
                                if (mapping) {
                                    "Mapping"
                                } else {
                                    "Accessor"
                                },
                                fieldType.className,
                                fieldAccessor.name,
                                if (constant) {
                                    "constant field value"
                                } else {
                                    "field"
                                }
                            )
                        }
                        addJavadoc(
                            """
                            
                            @since ${'$'}L
                            @version ${'$'}L
                            
                        """.trimIndent(),
                            lowestVersion.id,
                            highestVersion.id
                        )
                        if (!mapping) {
                            addJavadoc(
                                """
                                @see ${'$'}L#${'$'}L
                                """.trimIndent(),
                                mappingClassName.canonicalName(),
                                mappingName
                            )
                        }
                    }

                    val mod = fieldNode.last.value.modifiers
                    if ((mod and Opcodes.ACC_STATIC) != 0 && (mod and Opcodes.ACC_FINAL) != 0) { // constant
                        accessorBuilder.addField(
                            FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, JClassName.OBJECT), namingStrategy.constant(fieldAccessor, overloadIndex), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                .addMeta(constant = true)
                                .initializer("\$T.of(\$T.\$L::getConstantValue)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                .build()
                        )
                    } else {
                        when (generator.config.accessorType) {
                            AccessorType.REFLECTION -> {
                                accessorBuilder.addField(
                                    FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.FIELD), mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                        .addMeta()
                                        .initializer("\$T.of(\$T.\$L::getField)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                        .build()
                                )
                            }
                            AccessorType.METHOD_HANDLES -> {
                                accessorBuilder.addField(
                                    FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.METHOD_HANDLE), namingStrategy.fieldHandle(fieldAccessor, overloadIndex, false), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                        .addMeta()
                                        .initializer("\$T.of(\$T.\$L::getFieldGetter)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                        .build()
                                )
                                accessorBuilder.addField(
                                    FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.METHOD_HANDLE), namingStrategy.fieldHandle(fieldAccessor, overloadIndex, true), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                        .addMeta()
                                        .initializer("\$T.of(\$T.\$L::getFieldSetter)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                        .build()
                                )
                            }
                            AccessorType.NONE -> {}
                        }
                    }

                    FieldSpec.builder(types.FIELD_MAPPING, mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addMeta(mapping = true)
                        .initializer {
                            add("\$L.getField(\$S, \$L)", mappingFieldName, fieldAccessor.name, resolvedAccessor.fieldOverloads[fieldAccessor] ?: 0)
                            if (chain.isNotEmpty()) {
                                for ((chainedAccessor, _) in chain) {
                                    if (chainedAccessor === fieldAccessor) continue

                                    add(
                                        ".chain(\$L.getField(\$S, \$L))",
                                        mappingFieldName,
                                        chainedAccessor.name,
                                        resolvedAccessor.fieldOverloads[chainedAccessor] ?: 0
                                    )
                                }
                            }
                        }
                        .build()
                }.filterNotNull()
            )
            .addFields(
                resolvedAccessor.constructors.mapIndexed { i, (ctorAccessor, ctorNode) ->
                    val mappingName = namingStrategy.constructor(ctorAccessor, i)
                    val ctorArgs = Type.getArgumentTypes(ctorAccessor.type)

                    fun FieldSpec.Builder.addMeta(): FieldSpec.Builder = apply {
                        addAnnotation(types.NOT_NULL)
                        addJavadoc(
                            """
                                Accessor for the {@code (${'$'}L)} constructor.
                                
                                @since ${'$'}L
                                @version ${'$'}L
                                @see ${'$'}L#${'$'}L
                            """.trimIndent(),
                            ctorArgs.joinToString(transform = Type::getClassName),
                            ctorNode.first.key.id,
                            ctorNode.last.key.id,
                            mappingClassName.canonicalName(),
                            mappingName
                        )
                    }

                    when (generator.config.accessorType) {
                        AccessorType.REFLECTION -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.CONSTRUCTOR_WILDCARD), mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.\$L::getConstructor)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.METHOD_HANDLES -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.METHOD_HANDLE), mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.\$L::getConstructorHandle)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.NONE -> {}
                    }

                    FieldSpec.builder(types.CONSTRUCTOR_MAPPING, mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(types.NOT_NULL)
                        .addJavadoc(
                            """
                                Mapping for the {@code (${'$'}L)} constructor.
                                
                                @since ${'$'}L
                                @version ${'$'}L
                            """.trimIndent(),
                            ctorArgs.joinToString(transform = Type::getClassName),
                            ctorNode.first.key.id,
                            ctorNode.last.key.id
                        )
                        .initializer("\$L.getConstructor(\$L)", mappingFieldName, i)
                        .build()
                }
            )
            .addFields(
                resolvedAccessor.methods.map { (methodAccessor, methodNode) ->
                    if (methodAccessor.skipAccessorGeneration) {
                        return@map null
                    }

                    val methodType = if (methodAccessor.isIncomplete) getFriendlyType(methodNode.last.value) else Type.getType(methodAccessor.type)
                    val mappingName = namingStrategy.method(methodAccessor, resolvedAccessor.methodAccessorOverloads[methodAccessor] ?: 0)

                    val chain = ArrayList<ResolvedMethodPair>()

                    val lowestVersion: Version
                    val highestVersion: Version

                    if (methodAccessor.chain != null) {
                        var chainedAccessor: MethodAccessor? = methodAccessor
                        while (chainedAccessor != null) {
                            val chainedMethodNode = resolvedAccessor.methods.find { it.first == chainedAccessor }?.second
                                ?: throw RuntimeException("Chained method node should not be null at this point")
                            chain += ResolvedMethodPair(chainedAccessor!!, chainedMethodNode)
                            chainedAccessor = chainedAccessor!!.chain
                        }

                        lowestVersion = chain.minOf { it.second.first.key }
                        highestVersion = chain.maxOf { it.second.last.key }
                    } else {
                        lowestVersion = methodNode.first.key
                        highestVersion = methodNode.last.key
                    }

                    fun FieldSpec.Builder.addMeta(mapping: Boolean = false): FieldSpec.Builder = apply {
                        addAnnotation(types.NOT_NULL)

                        if (chain.isNotEmpty()) {
                            addJavadoc(
                                """
                                ${'$'}L for the following methods:
                                <ul>
                                
                                """.trimIndent(),
                                if (mapping) {
                                    "Mapping"
                                } else {
                                    "Accessor"
                                }
                            )

                            for ((chainedAccessor, chainedMethodNode) in chain) {
                                val chainedType =
                                    if (chainedAccessor.isIncomplete) getFriendlyType(chainedMethodNode.last.value) else Type.getType(chainedAccessor.type)
                                addJavadoc(
                                    """
                                    <li>{@code ${'$'}L ${'$'}L(${'$'}L)} (${'$'}L-${'$'}L)</li>
                                    
                                    """.trimIndent(),
                                    chainedType.returnType.className,
                                    chainedAccessor.name,
                                    chainedType.argumentTypes.joinToString(transform = Type::getClassName),
                                    chainedMethodNode.first.key.id,
                                    chainedMethodNode.last.key.id,
                                )
                            }

                            addJavadoc(
                                """
                                </ul>
                                
                                """.trimIndent()
                            )
                        } else {
                            addJavadoc(
                                """
                                ${'$'}L for the {@code ${'$'}L ${'$'}L(${'$'}L)} method.
                                
                                """.trimIndent(),
                                if (mapping) {
                                    "Mapping"
                                } else {
                                    "Accessor"
                                },
                                methodType.returnType.className,
                                methodAccessor.name,
                                methodType.argumentTypes.joinToString(transform = Type::getClassName)
                            )
                        }
                        addJavadoc(
                            """
                                
                            @since ${'$'}L
                            @version ${'$'}L
                            
                        """.trimIndent(),
                            lowestVersion.id,
                            highestVersion.id
                        )
                        if (!mapping) {
                            addJavadoc(
                                """
                                @see ${'$'}L#${'$'}L
                                """.trimIndent(),
                                mappingClassName.canonicalName(),
                                mappingName
                            )
                        }
                    }

                    when (generator.config.accessorType) {
                        AccessorType.REFLECTION -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.METHOD), mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.\$L::getMethod)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.METHOD_HANDLES -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(types.SUPPLIER, types.METHOD_HANDLE), mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.\$L::getMethodHandle)", types.LAZY_SUPPLIER, mappingClassName, mappingName)
                                    .build()
                            )
                        }
                        AccessorType.NONE -> {}
                    }

                    FieldSpec.builder(types.METHOD_MAPPING, mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addMeta(mapping = true)
                        .initializer {
                            add("\$L.getMethod(\$S, \$L)", mappingFieldName, methodAccessor.name, resolvedAccessor.methodOverloads[methodAccessor] ?: 0)
                            if (chain.isNotEmpty()) {
                                for ((chainedAccessor, _) in chain) {
                                    if (chainedAccessor === methodAccessor) continue

                                    add(
                                        ".chain(\$L.getMethod(\$S, \$L))",
                                        mappingFieldName,
                                        chainedAccessor.name,
                                        resolvedAccessor.methodOverloads[chainedAccessor] ?: 0
                                    )
                                }
                            }
                        }
                        .build()
                }.filterNotNull()
            )
            .build()
            .writeTo(mappingClassName, generator.workspace)

        if (generator.config.accessorType != AccessorType.NONE) {
            accessorBuilder.build()
                .writeTo(accessorClassName, generator.workspace)
        }
    }

    /**
     * Generates a Java mapping lookup class from class names.
     *
     * @param names fully qualified names of generated mapping classes
     */
    override fun generateLookupClass(names: List<String>) {
        val poolClassName = namingStrategy.klass(GeneratedClassType.LOOKUP).toJClassName()

        JTypeSpec.interfaceBuilder(poolClassName)
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(types.MAPPING_LOOKUP, namingStrategy.member(GeneratedMemberType.LOOKUP), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(types.NOT_NULL)
                    .addJavadoc("Mapping lookup index generated on {@code \$L}.", DATE_FORMAT.format(generationTime))
                    .initializer {
                        add("new \$T()", types.MAPPING_LOOKUP)
                        withIndent {
                            names.forEach { name ->
                                add("\n.put(\$T.\$L)", name.toJClassName(), namingStrategy.member(GeneratedMemberType.MAPPING))
                            }
                        }
                    }
                    .build()
            )
            .build()
            .writeTo(poolClassName, generator.workspace)
    }

    /**
     * Writes a [JTypeSpec] to a workspace with default settings.
     *
     * @param className the class name
     * @param workspace the workspace
     * @return the file where the source was actually written
     */
    fun JTypeSpec.writeTo(className: JClassName, workspace: Workspace): Path =
        JavaFile.builder(className.packageName(), this)
            .skipJavaLangImports(true)
            .addFileComment("This file was generated by takenaka on ${DATE_FORMAT.format(generationTime)}. Do not edit, changes will be overwritten!")
            .indent(" ".repeat(4)) // 4 spaces
            .build()
            .writeTo(workspace)
}

/**
 * Writes a [JavaFile] to a workspace.
 *
 * @param workspace the workspace
 * @return the file where the source was actually written
 */
fun JavaFile.writeTo(workspace: Workspace): Path = writeToPath(workspace.rootDirectory)
