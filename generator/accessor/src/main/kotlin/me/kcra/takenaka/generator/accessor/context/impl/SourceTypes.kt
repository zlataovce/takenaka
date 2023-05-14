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

import com.squareup.kotlinpoet.javapoet.*

/**
 * JavaPoet/KotlinPoet types.
 */
object SourceTypes {
    val CLASS_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "ClassMapping")
    val KT_CLASS_MAPPING = CLASS_MAPPING.toKClassName()
    val FIELD_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "FieldMapping")
    val KT_FIELD_MAPPING = FIELD_MAPPING.toKClassName()
    val CONSTRUCTOR_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "ConstructorMapping")
    val KT_CONSTRUCTOR_MAPPING = CONSTRUCTOR_MAPPING.toKClassName()
    val METHOD_MAPPING: JClassName = JClassName.get("me.kcra.takenaka.accessor.mapping", "MethodMapping")
    val KT_METHOD_MAPPING = METHOD_MAPPING.toKClassName()
    val NOT_NULL: JClassName = JClassName.get("org.jetbrains.annotations", "NotNull")
    val NULLABLE: JClassName = JClassName.get("org.jetbrains.annotations", "Nullable")
    val CLASS: JClassName = JClassName.get(java.lang.Class::class.java)
    val CLASS_WILDCARD: JParameterizedTypeName = JParameterizedTypeName.get(CLASS, JWildcardTypeName.subtypeOf(JClassName.OBJECT))
    val FIELD: JClassName = JClassName.get(java.lang.reflect.Field::class.java)
}
