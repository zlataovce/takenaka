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

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.kotlinpoet.javapoet.JClassName
import com.squareup.kotlinpoet.javapoet.JTypeSpec
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import org.objectweb.asm.Type
import java.nio.file.Path
import java.text.SimpleDateFormat
import javax.lang.model.element.Modifier

/**
 * A generation context that emits Java code.
 *
 * @property generator the generator
 * @param contextScope the coroutine scope of this context
 * @author Matouš Kučera
 */
open class JavaGenerationContext(override val generator: AccessorGenerator, contextScope: CoroutineScope) : AbstractGenerationContext(contextScope) {
    /**
     * Generates an accessor class from a model in Java.
     *
     * @param resolvedAccessor the accessor model
     */
    override fun generateClass0(resolvedAccessor: ResolvedClassAccessor) {
        val accessedQualifiedName = resolvedAccessor.model.name.fromInternalName()
        val accessedSimpleName = resolvedAccessor.model.internalName.substringAfterLast('/')

        val mappingClassName = JClassName.get(generator.config.basePackage, "${accessedSimpleName}Mapping")
        JTypeSpec.interfaceBuilder(mappingClassName)
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                """
                    Mappings for the {@code $accessedQualifiedName} class.
                    
                    @since ${resolvedAccessor.node.first.key.id}
                    @version ${resolvedAccessor.node.last.key.id}
                """.trimIndent().escape()
            )
            .addField(
                FieldSpec.builder(SourceTypes.CLASS_MAPPING, "MAPPING", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(SourceTypes.NOT_NULL)
                    .initializer(
                        CodeBlock.builder()
                            .add("new \$T()", SourceTypes.CLASS_MAPPING)
                            .indent()
                            .apply {
                                resolvedAccessor.node.forEach { (version, klass) ->
                                    generator.config.accessedNamespaces.forEach { ns ->
                                        klass.getName(ns)?.let { name ->
                                            // de-internalize the name beforehand to meet the ClassMapping contract
                                            add("\n.put(\$S, \$S, \$S)", version.id, ns, name.fromInternalName())
                                        }
                                    }
                                }

                                resolvedAccessor.fields.forEach { (fieldAccessor, fieldNode) ->
                                    add("\n.putField(\$S)", fieldAccessor.name)
                                    indent()

                                    fieldNode.forEach { (version, field) ->
                                        generator.config.accessedNamespaces.forEach { ns ->
                                            field.getName(ns)?.let { name ->
                                                add("\n.put(\$S, \$S, \$S)", version.id, ns, name)
                                            }
                                        }
                                    }

                                    add("\n.getParent()")
                                    unindent()
                                }

                                resolvedAccessor.constructors.forEach { (_, ctorNode) ->
                                    add("\n.putConstructor()")
                                    indent()

                                    ctorNode.forEach { (version, ctor) ->
                                        generator.config.accessedNamespaces.forEach { ns ->
                                            ctor.getDesc(ns)?.let { desc ->
                                                add("\n.put(\$S, \$S", version.id, ns)

                                                val args = Type.getArgumentTypes(desc)
                                                    .map { CodeBlock.of("\$S", it.className) }

                                                if (args.isNotEmpty()) {
                                                    add(", ")
                                                    add(CodeBlock.join(args, ", "))
                                                }

                                                add(")")
                                            }
                                        }
                                    }

                                    add("\n.getParent()")
                                    unindent()
                                }

                                resolvedAccessor.methods.forEach { (methodAccessor, methodNode) ->
                                    add("\n.putMethod(\$S)", methodAccessor.name)
                                    indent()

                                    methodNode.forEach { (version, method) ->
                                        generator.config.accessedNamespaces.forEach nsEach@ { ns ->
                                            val name = method.getName(ns) ?: return@nsEach
                                            val desc = method.getDesc(ns) ?: return@nsEach

                                            add("\n.put(\$S, \$S, \$S", version.id, ns, name)

                                            val args = Type.getArgumentTypes(desc)
                                                .map { CodeBlock.of("\$S", it.className) }

                                            if (args.isNotEmpty()) {
                                                add(", ")
                                                add(CodeBlock.join(args, ", "))
                                            }

                                            add(")")
                                        }
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

                    FieldSpec.builder(SourceTypes.FIELD_MAPPING, accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(SourceTypes.NOT_NULL)
                        .addJavadoc(
                            """
                                Mapping for the {@code ${fieldType.className} ${fieldAccessor.name}} field.
                                                    
                                @since ${fieldNode.first.key.id}
                                @version ${fieldNode.last.key.id}
                            """.trimIndent().escape()
                        )
                        .initializer(
                            CodeBlock.builder()
                                .add("MAPPING.getField(\$S)", fieldAccessor.name)
                                .apply {
                                    if (fieldAccessor.chain != null) {
                                        add(".chain(FIELD_${fieldAccessor.chain.upperName}${resolvedAccessor.fieldOverloads[fieldAccessor.chain]?.let { if (it != 0) "_$it" else "" } ?: ""})")
                                    }
                                }
                                .build()
                        )
                        .build()
                }
            )
            .addFields(
                resolvedAccessor.constructors.mapIndexed { i, (ctorAccessor, ctorNode) ->
                    val ctorArgs = Type.getArgumentTypes(ctorAccessor.type)

                    FieldSpec.builder(SourceTypes.CONSTRUCTOR_MAPPING, "CONSTRUCTOR_$i", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(SourceTypes.NOT_NULL)
                        .addJavadoc(
                            """
                                Mapping for the {@code (${ctorArgs.joinToString(transform = Type::getClassName)})} constructor.
                                                    
                                @since ${ctorNode.first.key.id}
                                @version ${ctorNode.last.key.id}
                            """.trimIndent().escape()
                        )
                        .initializer("MAPPING.getConstructor(\$L)", i)
                        .build()
                }
            )
            .addFields(
                resolvedAccessor.methods.map { (methodAccessor, methodNode) ->
                    val overloadIndex = resolvedAccessor.methodOverloads[methodAccessor]
                    val methodType = if (methodAccessor.isIncomplete) {
                        getFriendlyType(methodNode.last.value)
                    } else {
                        Type.getType(methodAccessor.type)
                    }

                    FieldSpec.builder(SourceTypes.METHOD_MAPPING, "METHOD_${methodAccessor.upperName}${overloadIndex?.let { if (it != 0) "_$it" else "" } ?: ""}", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(SourceTypes.NOT_NULL)
                        .addJavadoc(
                            """
                                Mapping for the {@code ${methodType.returnType.className} ${methodAccessor.name}(${methodType.argumentTypes.joinToString(transform = Type::getClassName)})} method.
                                                    
                                @since ${methodNode.first.key.id}
                                @version ${methodNode.last.key.id}
                            """.trimIndent().escape()
                        )
                        .initializer(
                            CodeBlock.builder()
                                .add("MAPPING.getMethod(\$S, \$L)", methodAccessor.name, overloadIndex)
                                .apply {
                                    if (methodAccessor.chain != null) {
                                        add(".chain(METHOD_${methodAccessor.chain.upperName}${resolvedAccessor.methodOverloads[methodAccessor.chain]?.let { if (it != 0) "_$it" else "" } ?: ""})")
                                    }
                                }
                                .build()
                        )
                        .build()
                }
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

    companion object {
        /**
         * The file comment's generation timestamp date format.
         */
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }
}

/**
 * Writes a [JavaFile] to a workspace.
 *
 * @param workspace the workspace
 * @return the file where the source was actually written
 */
fun JavaFile.writeTo(workspace: Workspace): Path = writeToPath(workspace.rootDirectory)

/**
 * Escapes JavaPoet formatting symbols.
 *
 * @return the escaped string
 */
fun String.escape(): String = replace("$", "$$")
