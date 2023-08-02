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
    override fun generateClass0(resolvedAccessor: ResolvedClassAccessor) {
        val accessedQualifiedName = resolvedAccessor.model.name.fromInternalName()
        val accessedSimpleName = resolvedAccessor.model.internalName.substringAfterLast('/')

        val mappingClassName = KClassName(generator.config.basePackage, "${accessedSimpleName}Mapping")
        val typeSpecs = buildList {
            add(
                KTypeSpec.objectBuilder(mappingClassName)
                    .addKdoc(
                        """
                            Mappings for the `%L` class.
                            
                            Last version: %L
                            @since %L
                        """.trimIndent(),
                        accessedQualifiedName,
                        resolvedAccessor.node.last.key.id,
                        resolvedAccessor.node.first.key.id
                    )
                    .addProperty(
                        PropertySpec.builder("MAPPING", SourceTypes.KT_CLASS_MAPPING)
                            .initializer(
                                buildCodeBlock {
                                    add("%T()", SourceTypes.KT_CLASS_MAPPING)
                                    withIndent {
                                        groupClassNames(resolvedAccessor.node).forEach { (classKey, versions) ->
                                            val (ns, name) = classKey

                                            add(
                                                "\n.put(%S, %S, %L)",
                                                ns,
                                                name,
                                                versions.map { KCodeBlock.of("%S", it.id) }.joinToCode()
                                            )
                                        }
                                        
                                        resolvedAccessor.fields.forEach { (fieldAccessor, fieldNode) ->
                                            add("\n.putField(%S)", fieldAccessor.name)
                                            withIndent {
                                                groupFieldNames(fieldNode).forEach { (fieldKey, versions) ->
                                                    val (ns, name) = fieldKey

                                                    add(
                                                        "\n.put(%S, %S, %L)",
                                                        ns,
                                                        name,
                                                        versions.map { KCodeBlock.of("%S", it.id) }.joinToCode()
                                                    )
                                                }

                                                add("\n.getParent()")
                                            }
                                        }

                                        resolvedAccessor.constructors.forEach { (_, ctorNode) ->
                                            add("\n.putConstructor()")
                                            withIndent {
                                                groupConstructorNames(ctorNode).forEach { (ctorKey, versions) ->
                                                    val (ns, desc) = ctorKey

                                                    add(
                                                        "\n.put(%S, arrayOf(%L)",
                                                        ns,
                                                        versions.map { KCodeBlock.of("%S", it.id) }.joinToCode()
                                                    )

                                                    val args = Type.getArgumentTypes(desc)
                                                        .map { KCodeBlock.of("%S", it.className) }

                                                    if (args.isNotEmpty()) {
                                                        add(", ")
                                                        add(args.joinToCode())
                                                    }

                                                    add(")")
                                                }

                                                add("\n.getParent()")
                                            }
                                        }

                                        resolvedAccessor.methods.forEach { (methodAccessor, methodNode) ->
                                            add("\n.putMethod(%S)", methodAccessor.name)
                                            withIndent {
                                                groupMethodNames(methodNode).forEach { (methodKey, versions) ->
                                                    val (ns, name, desc) = methodKey

                                                    add(
                                                        "\n.put(%S, arrayOf(%L), %S",
                                                        ns,
                                                        versions.map { KCodeBlock.of("%S", it.id) }.joinToCode(),
                                                        name
                                                    )

                                                    val args = Type.getArgumentTypes(desc)
                                                        .map { KCodeBlock.of("%S", it.className) }

                                                    if (args.isNotEmpty()) {
                                                        add(", ")
                                                        add(args.joinToCode())
                                                    }

                                                    add(")")
                                                }

                                                add("\n.getParent()")
                                            }
                                        }
                                    }
                                }
                            )
                            .build()
                    )
                    .build()
            )
        }

        typeSpecs.writeTo(accessedSimpleName.replaceFirstChar { it.lowercase(Locale.getDefault()) }, generator.workspace)
    }

    /**
     * Writes a [KTypeSpec] to a workspace with default settings.
     *
     * @param workspace the workspace
     */
    fun KTypeSpec.writeTo(workspace: Workspace) {
        listOf(this).writeTo(requireNotNull(this.name) { "File name required, but type has no name" }, workspace)
    }

    /**
     * Writes [KTypeSpec]s to a single file in a workspace with default settings.
     *
     * @param name the file name
     * @param workspace the workspace
     */
    fun Iterable<KTypeSpec>.writeTo(name: String, workspace: Workspace) {
        FileSpec.builder(generator.config.basePackage, name)
            .addFileComment("This file was generated by takenaka on ${DATE_FORMAT.format(generationTime)}. Do not edit, changes will be overwritten!")
            .addKotlinDefaultImports(includeJvm = true)
            .indent(" ".repeat(4)) // 4 spaces
            .apply {
                forEach(::addType)
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
