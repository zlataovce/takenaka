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

package me.kcra.takenaka.generator.accessor.context

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.mapping.ancestry.impl.ClassAncestryNode
import me.kcra.takenaka.core.mapping.ancestry.impl.FieldAncestryNode
import me.kcra.takenaka.core.mapping.ancestry.impl.fieldAncestryTreeOf
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import mu.KotlinLogging
import org.objectweb.asm.Type
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import javax.lang.model.element.Modifier

private val logger = KotlinLogging.logger {}

/**
 * A generation context that emits Java code.
 *
 * @property generator the generator
 * @param contextScope the coroutine scope of this context
 * @author Matouš Kučera
 */
open class JavaGenerationContext(
    override val generator: AccessorGenerator,
    contextScope: CoroutineScope
) : GenerationContext, CoroutineScope by contextScope {
    /**
     * The generation timestamp of this context's output.
     */
    val generationTime by lazy(::Date)

    /**
     * Generates an accessor class from a model in Java.
     *
     * @param model the accessor model
     * @param node the ancestry node of the class defined by the model
     */
    override fun generateClass(model: ClassAccessor, node: ClassAncestryNode) {
        val fieldTree = fieldAncestryTreeOf(node)

        val mappedFields = mutableListOf<Pair<FieldAccessor, FieldAncestryNode>>()
        val spec = TypeSpec.interfaceBuilder("${model.internalName.substringAfterLast('/')}Accessor")
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(
                """
                    Accessors for the {@code ${model.name.fromInternalName()}} class.
                    
                    @since ${node.first.key.id}
                    @version ${node.last.key.id}
                """.trimIndent()
            )
            .addField(
                FieldSpec.builder(SourceTypes.CLASS_MAPPING, "MAPPING", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer(
                        CodeBlock.builder()
                            .add("new \$T()", SourceTypes.CLASS_MAPPING)
                            .indent()
                            .apply {
                                node.forEach { (version, klass) ->
                                    generator.config.accessedNamespaces.forEach { ns ->
                                        klass.getName(ns)?.let { name ->
                                            // de-internalize the name beforehand to meet the ClassMapping contract
                                            add("\n.put(\$S, \$S, \$S)", version.id, ns, name.fromInternalName())
                                        }
                                    }
                                }

                                model.fields.forEach { fieldAccessor ->
                                    // TODO: infer type if null
                                    val fieldNode = fieldTree[fieldAccessor.name to fieldAccessor.internalType]
                                    if (fieldNode == null) {
                                        logger.warn { "did not find field ancestry node with name ${fieldAccessor.name} and type ${fieldAccessor.internalType}" }
                                        return@forEach
                                    }

                                    add("\n.putField()")
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

                                    mappedFields += fieldAccessor to fieldNode
                                }
                            }
                            .unindent()
                            .build()
                    )
                    .build()
            )
            .addField(
                FieldSpec.builder(SourceTypes.CLASS_WILDCARD, "TYPE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addAnnotation(SourceTypes.NULLABLE)
                    .addJavadoc(
                        """
                            The {@link java.lang.Class} object of the accessed class,
                            null if it is not present in mappings of the current platform.
                            
                            @see me.kcra.takenaka.accessor.platform.MapperPlatforms#getCurrentPlatform()
                        """.trimIndent()
                    )
                    .initializer("MAPPING.getClassByCurrentPlatform()")
                    .build()
            )
            .apply {
                mappedFields.forEachIndexed { index, (fieldAccessor, fieldNode) ->
                    val accessorName = fieldAccessor.name.camelToUpperSnakeCase()
                    val mappingName = "${accessorName}_MAPPING"

                    addField(
                        FieldSpec.builder(SourceTypes.FIELD_MAPPING, mappingName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .addAnnotation(SourceTypes.NOT_NULL)
                            .addJavadoc(
                                """
                                    Mapping for the {@code ${fieldAccessor.name}} field of type {@code ${Type.getType(getFriendlyDstDesc(fieldNode.last.value)).className}}.
                                                        
                                    @since ${fieldNode.first.key.id}
                                    @version ${fieldNode.last.key.id}
                                """.trimIndent()
                            )
                            .initializer("MAPPING.getField(\$L)", index)
                            .build()
                    )
                    addField(
                        FieldSpec.builder(java.lang.reflect.Field::class.java, accessorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .addAnnotation(SourceTypes.NULLABLE)
                            .addJavadoc(
                                """
                                    The {@link java.lang.reflect.Field} object of the {@code #$mappingName} field mapping,
                                    null if it is not present in mappings of the current platform.
                                    
                                    @see me.kcra.takenaka.accessor.platform.MapperPlatforms#getCurrentPlatform()
                                """.trimIndent()
                            )
                            .initializer("$mappingName.getFieldByCurrentPlatform()", index)
                            .build()
                    )
                }
            }
            .build()

        JavaFile.builder(generator.config.basePackage, spec)
            .skipJavaLangImports(true)
            .addFileComment("This file was generated by takenaka on ${DATE_FORMAT.format(generationTime)}. Do not edit, changes will be overwritten!")
            .indent(" ".repeat(4)) // 4 spaces
            .build()
            .writeTo(generator.workspace)
    }

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

val CAMEL_REGEX = "(?<=[a-zA-Z])[A-Z]".toRegex()
fun String.camelToUpperSnakeCase(): String = CAMEL_REGEX.replace(this) { "_${it.value}" }.uppercase()
