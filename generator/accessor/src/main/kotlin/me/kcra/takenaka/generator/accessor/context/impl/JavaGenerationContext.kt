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

import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.kotlinpoet.javapoet.JClassName
import com.squareup.kotlinpoet.javapoet.JParameterizedTypeName
import com.squareup.kotlinpoet.javapoet.JTypeSpec
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.generator.accessor.AccessorType
import me.kcra.takenaka.generator.accessor.AccessorGenerator
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
        val accessedSimpleName = resolvedAccessor.model.internalName.substringAfterLast('/')

        val mappingClassName = JClassName.get(generator.config.basePackage, "${accessedSimpleName}Mapping")
        val accessorClassName = JClassName.get(generator.config.basePackage, "${accessedSimpleName}Accessor")
        val accessorBuilder = JTypeSpec.interfaceBuilder(accessorClassName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                """
                    Accessors for the {@code ${'$'}L} class.
                    
                    @see ${'$'}L
                """.trimIndent(),
                accessedQualifiedName,
                mappingClassName.canonicalName()
            )
            .addField(
                FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.CLASS_WILDCARD), "TYPE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(SourceTypes.NOT_NULL)
                    .initializer("\$T.of(\$T.MAPPING::getClazz)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
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
                FieldSpec.builder(SourceTypes.CLASS_MAPPING, "MAPPING", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(SourceTypes.NOT_NULL)
                    .initializer(
                        JCodeBlock.builder()
                            .add("new \$T(\$S)", SourceTypes.CLASS_MAPPING, accessedQualifiedName)
                            .indent()
                            .apply {
                                groupClassNames(resolvedAccessor.node).forEach { (classKey, versions) ->
                                    val (ns, name) = classKey

                                    add("\n.put(\$S, \$S, \$L)", ns, name, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "))
                                }
                            }
                            .apply {
                                resolvedAccessor.fields.forEach { (fieldAccessor, fieldNode) ->
                                    add("\n.putField(\$S)", fieldAccessor.name)
                                    indent()

                                    groupFieldNames(fieldNode).forEach { (fieldKey, versions) ->
                                        val (ns, name) = fieldKey

                                        add("\n.put(\$S, \$S, \$L)", ns, name, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "))
                                    }

                                    add("\n.getParent()")
                                    unindent()
                                }
                            }
                            .apply {
                                resolvedAccessor.constructors.forEach { (_, ctorNode) ->
                                    add("\n.putConstructor()")
                                    indent()

                                    groupConstructorNames(ctorNode).forEach { (ctorKey, versions) ->
                                        val (ns, desc) = ctorKey

                                        add("\n.put(\$S, new \$T[] { \$L }", ns, SourceTypes.STRING, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "))

                                        val args = Type.getArgumentTypes(desc)
                                            .map { JCodeBlock.of("\$S", it.className) }

                                        if (args.isNotEmpty()) {
                                            add(", ")
                                            add(JCodeBlock.join(args, ", "))
                                        }

                                        add(")")
                                    }

                                    add("\n.getParent()")
                                    unindent()
                                }
                            }
                            .apply {
                                resolvedAccessor.methods.forEach { (methodAccessor, methodNode) ->
                                    add("\n.putMethod(\$S)", methodAccessor.name)
                                    indent()

                                    groupMethodNames(methodNode).forEach { (methodKey, versions) ->
                                        val (ns, name, desc) = methodKey

                                        add("\n.put(\$S, new \$T[] { \$L }, \$S", ns, SourceTypes.STRING, JCodeBlock.join(versions.map { JCodeBlock.of("\$S", it.id) }, ", "), name)

                                        val args = Type.getArgumentTypes(desc)
                                            .map { JCodeBlock.of("\$S", it.className) }

                                        if (args.isNotEmpty()) {
                                            add(", ")
                                            add(JCodeBlock.join(args, ", "))
                                        }

                                        add(")")
                                    }

                                    add("\n.getParent()")
                                    unindent()
                                }
                            }
                            .unindent()
                            .build()
                    )
                    .build()
            )
            .addFields(
                resolvedAccessor.fields.map { (fieldAccessor, fieldNode) ->
                    val accessorName = "FIELD_${fieldAccessor.upperName}${resolvedAccessor.fieldOverloads[fieldAccessor]?.let { if (it != 0) "_$it" else "" } ?: ""}"
                    val fieldType = fieldAccessor.type?.let(Type::getType)
                        ?: getFriendlyType(fieldNode.last.value)

                    fun FieldSpec.Builder.addMeta(constant: Boolean = false): FieldSpec.Builder = apply {
                        addAnnotation(SourceTypes.NOT_NULL)
                        addJavadoc(
                            """
                                Accessor for the {@code ${'$'}L ${'$'}L} ${'$'}L.
                                
                                @see ${'$'}L#${'$'}L
                            """.trimIndent(),
                            fieldType.className,
                            fieldAccessor.name,
                            if (constant) {
                                "constant field value"
                            } else {
                                "field"
                            },
                            mappingClassName.canonicalName(),
                            accessorName
                        )
                    }

                    val mod = fieldNode.last.value.modifiers
                    if ((mod and Opcodes.ACC_STATIC) != 0 && (mod and Opcodes.ACC_FINAL) != 0) { // constant
                        accessorBuilder.addField(
                            FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, JClassName.OBJECT), accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                .addMeta(constant = true)
                                .initializer("\$T.of(\$T.$accessorName::getConstantValue)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                .build()
                        )
                    } else {
                        when (generator.config.accessorType) {
                            AccessorType.REFLECTION -> {
                                accessorBuilder.addField(
                                    FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.FIELD), accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                        .addMeta()
                                        .initializer("\$T.of(\$T.$accessorName::getField)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                        .build()
                                )
                            }
                            AccessorType.METHOD_HANDLES -> {
                                accessorBuilder.addField(
                                    FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.METHOD_HANDLE), "${accessorName}_GETTER", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                        .addMeta()
                                        .initializer("\$T.of(\$T.$accessorName::getFieldGetter)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                        .build()
                                )
                                accessorBuilder.addField(
                                    FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.METHOD_HANDLE), "${accessorName}_SETTER", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                        .addMeta()
                                        .initializer("\$T.of(\$T.$accessorName::getFieldSetter)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                        .build()
                                )
                            }
                            AccessorType.NONE -> {}
                        }
                    }

                    FieldSpec.builder(SourceTypes.FIELD_MAPPING, accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(SourceTypes.NOT_NULL)
                        .addJavadoc(
                            """
                                Mapping for the {@code ${'$'}L ${'$'}L} field.
                                
                                @since ${'$'}L
                                @version ${'$'}L
                            """.trimIndent(),
                            fieldType.className,
                            fieldAccessor.name,
                            fieldNode.first.key.id,
                            fieldNode.last.key.id
                        )
                        .initializer(
                            JCodeBlock.builder()
                                .add("MAPPING.getField(\$S)", fieldAccessor.name)
                                .apply {
                                    if (fieldAccessor.chain != null) {
                                        add(
                                            ".chain(FIELD_\$L\$L)",
                                            fieldAccessor.chain.upperName,
                                            resolvedAccessor.fieldOverloads[fieldAccessor.chain]
                                                ?.let { if (it != 0) "_$it" else "" }
                                                ?: ""
                                        )
                                    }
                                }
                                .build()
                        )
                        .build()
                }
            )
            .addFields(
                resolvedAccessor.constructors.mapIndexed { i, (ctorAccessor, ctorNode) ->
                    val accessorName = "CONSTRUCTOR_$i"
                    val ctorArgs = Type.getArgumentTypes(ctorAccessor.type)

                    fun FieldSpec.Builder.addMeta(): FieldSpec.Builder = apply {
                        addAnnotation(SourceTypes.NOT_NULL)
                        addJavadoc(
                            """
                                Accessor for the {@code (${'$'}L)} constructor.
                                
                                @see ${'$'}L#${'$'}L
                            """.trimIndent(),
                            ctorArgs.joinToString(transform = Type::getClassName),
                            mappingClassName.canonicalName(),
                            accessorName
                        )
                    }

                    when (generator.config.accessorType) {
                        AccessorType.REFLECTION -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.CONSTRUCTOR_WILDCARD), accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.$accessorName::getConstructor)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                    .build()
                            )
                        }
                        AccessorType.METHOD_HANDLES -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.METHOD_HANDLE), accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.$accessorName::getConstructorHandle)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                    .build()
                            )
                        }
                        AccessorType.NONE -> {}
                    }

                    FieldSpec.builder(SourceTypes.CONSTRUCTOR_MAPPING, accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(SourceTypes.NOT_NULL)
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
                        .initializer("MAPPING.getConstructor(\$L)", i)
                        .build()
                }
            )
            .addFields(
                resolvedAccessor.methods.map { (methodAccessor, methodNode) ->
                    val overloadIndex = resolvedAccessor.methodOverloads[methodAccessor]
                    val accessorName = "METHOD_${methodAccessor.upperName}${overloadIndex?.let { if (it != 0) "_$it" else "" } ?: ""}"
                    val methodType = if (methodAccessor.isIncomplete) {
                        getFriendlyType(methodNode.last.value)
                    } else {
                        Type.getType(methodAccessor.type)
                    }

                    fun FieldSpec.Builder.addMeta(): FieldSpec.Builder = apply {
                        addAnnotation(SourceTypes.NOT_NULL)
                        addJavadoc(
                            """
                                Accessor for the {@code ${'$'}L ${'$'}L(${'$'}L)} method.
                                
                                @see ${'$'}L#${'$'}L
                            """.trimIndent(),
                            methodType.returnType.className,
                            methodAccessor.name,
                            methodType.argumentTypes.joinToString(transform = Type::getClassName),
                            mappingClassName.canonicalName(),
                            accessorName
                        )
                    }

                    when (generator.config.accessorType) {
                        AccessorType.REFLECTION -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.METHOD), accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.$accessorName::getMethod)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                    .build()
                            )
                        }
                        AccessorType.METHOD_HANDLES -> {
                            accessorBuilder.addField(
                                FieldSpec.builder(JParameterizedTypeName.get(SourceTypes.SUPPLIER, SourceTypes.METHOD_HANDLE), accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                                    .addMeta()
                                    .initializer("\$T.of(\$T.$accessorName::getMethodHandle)", SourceTypes.LAZY_SUPPLIER, mappingClassName)
                                    .build()
                            )
                        }
                        AccessorType.NONE -> {}
                    }

                    FieldSpec.builder(SourceTypes.METHOD_MAPPING, accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(SourceTypes.NOT_NULL)
                        .addJavadoc(
                            """
                                Mapping for the {@code ${'$'}L ${'$'}L(${'$'}L)} method.
                                
                                @since ${'$'}L
                                @version ${'$'}L
                            """.trimIndent(),
                            methodType.returnType.className,
                            methodAccessor.name,
                            methodType.argumentTypes.joinToString(transform = Type::getClassName),
                            methodNode.first.key.id,
                            methodNode.last.key.id
                        )
                        .initializer(
                            JCodeBlock.builder()
                                .add("MAPPING.getMethod(\$S, \$L)", methodAccessor.name, overloadIndex)
                                .apply {
                                    if (methodAccessor.chain != null) {
                                        add(
                                            ".chain(METHOD_\$L\$L)",
                                            methodAccessor.chain.upperName,
                                            resolvedAccessor.methodOverloads[methodAccessor.chain]
                                                ?.let { if (it != 0) "_$it" else "" }
                                                ?: ""
                                        )
                                    }
                                }
                                .build()
                        )
                        .build()
                }
            )
            .build()
            .writeTo(generator.workspace)

        if (generator.config.accessorType != AccessorType.NONE) {
            accessorBuilder.build().writeTo(generator.workspace)
        }
    }

    /**
     * Generates a Java mapping lookup class from class names.
     *
     * @param names internal names of classes declared in accessor models
     */
    override fun generateLookupClass(names: List<String>) {
        val poolClassName = JClassName.get(generator.config.basePackage, "Mappings")

        JTypeSpec.interfaceBuilder(poolClassName)
            .addModifiers(Modifier.PUBLIC)
            .addField(
                FieldSpec.builder(SourceTypes.MAPPING_LOOKUP, "LOOKUP", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(SourceTypes.NOT_NULL)
                    .addJavadoc("Mapping lookup index generated on {@code \$L}.", DATE_FORMAT.format(generationTime))
                    .initializer(
                        JCodeBlock.builder()
                            .add("new \$T()", SourceTypes.MAPPING_LOOKUP)
                            .indent()
                            .apply {
                                names.forEach { name ->
                                    val mappingClassName = JClassName.get(
                                        generator.config.basePackage,
                                        "${name.substringAfterLast('/')}Mapping"
                                    )

                                    add("\n.put(\$T.MAPPING)", mappingClassName)
                                }
                            }
                            .unindent()
                            .build()
                    )
                    .build()
            )
            .build()
            .writeTo(generator.workspace)
    }

    /**
     * Writes a [JTypeSpec] to a workspace with default settings.
     *
     * @param workspace the workspace
     * @return the file where the source was actually written
     */
    fun JTypeSpec.writeTo(workspace: Workspace): Path =
        JavaFile.builder(generator.config.basePackage, this)
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
