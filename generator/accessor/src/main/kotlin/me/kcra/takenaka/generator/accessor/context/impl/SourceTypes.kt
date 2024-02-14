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

import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.javapoet.*

typealias JCodeBlock = com.squareup.javapoet.CodeBlock
typealias JCodeBlockBuilder = com.squareup.javapoet.CodeBlock.Builder

typealias KCodeBlock = com.squareup.kotlinpoet.CodeBlock
typealias KCodeBlockBuilder = com.squareup.kotlinpoet.CodeBlock.Builder

/**
 * Builds new [JCodeBlock] by populating newly created [JCodeBlockBuilder] using provided
 * [builderAction] and then converting it to [JCodeBlock].
 */
inline fun buildJCodeBlock(builderAction: JCodeBlockBuilder.() -> Unit): JCodeBlock {
    return JCodeBlock.builder().apply(builderAction).build()
}

/**
 * Calls [JCodeBlockBuilder.indent] then executes the provided [builderAction] on the
 * [JCodeBlockBuilder] and then executes [JCodeBlockBuilder.unindent] before returning the
 * original [JCodeBlockBuilder].
 */
inline fun JCodeBlockBuilder.withIndent(builderAction: JCodeBlockBuilder.() -> Unit): JCodeBlockBuilder {
    return indent().also(builderAction).unindent()
}

/**
 * Builds and sets the initializer for this field builder.
 *
 * @param block the code block builder action
 * @return the field builder
 */
inline fun com.squareup.javapoet.FieldSpec.Builder.initializer(block: JCodeBlockBuilder.() -> Unit): com.squareup.javapoet.FieldSpec.Builder =
    initializer(buildJCodeBlock(block))

/**
 * Builds and sets the initializer for this property builder.
 *
 * @param block the code block builder action
 * @return the property builder
 */
inline fun com.squareup.kotlinpoet.PropertySpec.Builder.initializer(block: KCodeBlockBuilder.() -> Unit): com.squareup.kotlinpoet.PropertySpec.Builder =
    initializer(buildCodeBlock(block))

/**
 * Adds a member import to the file.
 *
 * @param memberName the member
 * @return the file builder
 */
fun com.squareup.kotlinpoet.FileSpec.Builder.addImport(memberName: MemberName): com.squareup.kotlinpoet.FileSpec.Builder =
    memberName.run { addImport("$packageName${enclosingClassName?.let { ".$it" } ?: ""}", simpleName) }

/**
 * JavaPoet/KotlinPoet types.
 */
object SourceTypes {
    val MAPPING_LOOKUP: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "MappingLookup")
    val KT_MAPPING_LOOKUP = MAPPING_LOOKUP.toKClassName()
    val CLASS_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "ClassMapping")
    val KT_CLASS_MAPPING = CLASS_MAPPING.toKClassName()
    val FIELD_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "FieldMapping")
    val KT_FIELD_MAPPING = FIELD_MAPPING.toKClassName()
    val CONSTRUCTOR_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "ConstructorMapping")
    val KT_CONSTRUCTOR_MAPPING = CONSTRUCTOR_MAPPING.toKClassName()
    val METHOD_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "MethodMapping")
    val KT_METHOD_MAPPING = METHOD_MAPPING.toKClassName()
    val SUPPLIER: JClassName = JClassName.get(java.util.function.Supplier::class.java)
    val LAZY_SUPPLIER: JClassName = JClassName.get("me.kcra.takenaka.accessor.util", "LazySupplier")
    val KT_LAZY_SUPPLIER = LAZY_SUPPLIER.toKClassName()
    val NOT_NULL: JClassName = JClassName.get("org.jetbrains.annotations", "NotNull")
    val NULLABLE: JClassName = JClassName.get("org.jetbrains.annotations", "Nullable")
    val CLASS: JClassName = JClassName.get(java.lang.Class::class.java)
    val CLASS_WILDCARD: JParameterizedTypeName = JParameterizedTypeName.get(CLASS, JWildcardTypeName.subtypeOf(JClassName.OBJECT))
    val KT_CLASS_WILDCARD = CLASS_WILDCARD.toKParameterizedTypeName()
    val KT_NULLABLE_CLASS_WILDCARD = KT_CLASS_WILDCARD.copy(nullable = true)
    val FIELD: JClassName = JClassName.get(java.lang.reflect.Field::class.java)
    val KT_FIELD = FIELD.toKClassName()
    val KT_NULLABLE_FIELD = KT_FIELD.copy(nullable = true)
    val CONSTRUCTOR: JClassName = JClassName.get(java.lang.reflect.Constructor::class.java)
    val CONSTRUCTOR_WILDCARD: JParameterizedTypeName = JParameterizedTypeName.get(CONSTRUCTOR, JWildcardTypeName.subtypeOf(JClassName.OBJECT))
    val KT_CONSTRUCTOR_WILDCARD = CONSTRUCTOR_WILDCARD.toKParameterizedTypeName()
    val KT_NULLABLE_CONSTRUCTOR_WILDCARD = KT_CONSTRUCTOR_WILDCARD.copy(nullable = true)
    val METHOD: JClassName = JClassName.get(java.lang.reflect.Method::class.java)
    val KT_METHOD = METHOD.toKClassName()
    val KT_NULLABLE_METHOD = KT_METHOD.copy(nullable = true)
    val METHOD_HANDLE: JClassName = JClassName.get(java.lang.invoke.MethodHandle::class.java)
    val KT_METHOD_HANDLE = METHOD_HANDLE.toKClassName()
    val KT_NULLABLE_METHOD_HANDLE = KT_METHOD_HANDLE.copy(nullable = true)
    val STRING: JClassName = JClassName.get(java.lang.String::class.java)
    val KT_NULLABLE_ANY = com.squareup.kotlinpoet.ANY.copy(nullable = true)

    val KT_MAPPING_LOOKUP_DSL = MemberName("me.kcra.takenaka.accessor.util.kotlin", "mappingLookup")
    val KT_CLASS_MAPPING_DSL = MemberName("me.kcra.takenaka.accessor.util.kotlin", "classMapping")
    val KT_CLASS_MAPPING_FIELD_DSL = MemberName("me.kcra.takenaka.accessor.util.kotlin", "field", isExtension = true)
    val KT_CLASS_MAPPING_CTOR_DSL = MemberName("me.kcra.takenaka.accessor.util.kotlin", "constructor", isExtension = true)
    val KT_CLASS_MAPPING_METHOD_DSL = MemberName("me.kcra.takenaka.accessor.util.kotlin", "method", isExtension = true)
    val KT_LAZY_DSL = MemberName("me.kcra.takenaka.accessor.util.kotlin", "lazy")
    val KT_LAZY_DELEGATE_DSL = MemberName("me.kcra.takenaka.accessor.util.kotlin", "getValue", isExtension = true)
}
