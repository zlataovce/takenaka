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
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.common.provider.AncestryProvider
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
        val accessedSimpleName = resolvedAccessor.model.internalName.substringAfterLast('/')

        val mappingClassName = KClassName(generator.config.basePackage, "${accessedSimpleName}Mapping")
        val typeSpecs = buildList<Any> {
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
                    .addProperty(
                        PropertySpec.builder("MAPPING", SourceTypes.KT_CLASS_MAPPING)
                            .initializer(
                                buildCodeBlock {
                                    beginControlFlow("%T", SourceTypes.CLASS_MAPPING_DSL)

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
                                        beginControlFlow("%T(%S)", SourceTypes.CLASS_MAPPING_FIELD_DSL, fieldAccessor.name)

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
                                        beginControlFlow("%T", SourceTypes.CLASS_MAPPING_CTOR_DSL)

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
                                        beginControlFlow("%T(%S)", SourceTypes.CLASS_MAPPING_METHOD_DSL, methodAccessor.name)

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

                                    endControlFlow()
                                }
                            )
                            .build()
                    )
                    .build()

                // TODO: add mapping breakout fields
            )

            // TODO: add accessor classes
        }

        typeSpecs.writeTo(generator.workspace, accessedSimpleName.replaceFirstChar { it.lowercase(Locale.getDefault()) })
    }

    /**
     * Generates a Kotlin mapping lookup class from class names.
     *
     * @param names internal names of classes declared in accessor models
     */
    override fun generateLookupClass(names: List<String>) {
        PropertySpec.builder("LOOKUP", SourceTypes.KT_MAPPING_LOOKUP)
            .addKdoc("Mapping lookup index.")
            .initializer(
                buildCodeBlock {
                    beginControlFlow("%T", SourceTypes.MAPPING_LOOKUP_DSL)

                    names.forEach { name ->
                        val mappingClassName = KClassName(
                            generator.config.basePackage,
                            "${name.substringAfterLast('/')}Mapping"
                        )

                        addStatement("put(%T.MAPPING)", mappingClassName)
                    }

                    endControlFlow()
                }
            )
            .build()
            .writeTo(generator.workspace, "lookup")
    }

    /**
     * Writes a [PropertySpec] to a workspace with default settings.
     *
     * @param workspace the workspace
     * @param name the file name
     */
    fun PropertySpec.writeTo(workspace: Workspace, name: String = requireNotNull(this.name) { "File name required, but type has no name" }) {
        listOf(this).writeTo(workspace, name)
    }

    /**
     * Writes KotlinPoet elements ([KTypeSpec], [FunSpec], [PropertySpec], [TypeAliasSpec]) to a single file in a workspace with default settings.
     *
     * @param workspace the workspace
     * @param name the file name
     */
    fun Iterable<Any>.writeTo(workspace: Workspace, name: String) {
        FileSpec.builder(generator.config.basePackage, name)
            .addFileComment("This file was generated by takenaka on ${DATE_FORMAT.format(generationTime)}. Do not edit, changes will be overwritten!")
            .addKotlinDefaultImports(includeJvm = true)
            .indent(" ".repeat(4)) // 4 spaces
            .apply {
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
