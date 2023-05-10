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

package me.kcra.takenaka.generator.accessor.context

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.WildcardTypeName
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import com.squareup.kotlinpoet.javapoet.toKClassName

/**
 * JavaPoet/KotlinPoet types.
 */
object SourceTypes {
    val CLASS_MAPPING: ClassName = ClassName.get("me.kcra.takenaka.accessor.mapping", "ClassMapping")
    val KT_CLASS_MAPPING = CLASS_MAPPING.toKClassName()
    val FIELD_MAPPING: ClassName = ClassName.get("me.kcra.takenaka.accessor.mapping", "FieldMapping")
    val KT_FIELD_MAPPING = FIELD_MAPPING.toKClassName()
    val NOT_NULL: ClassName = ClassName.get("org.jetbrains.annotations", "NotNull")
    val NULLABLE: ClassName = ClassName.get("org.jetbrains.annotations", "Nullable")
    val CLASS: ClassName = ClassName.get(java.lang.Class::class.java)
    val CLASS_WILDCARD: ParameterizedTypeName = ParameterizedTypeName.get(CLASS, WildcardTypeName.subtypeOf(ClassName.OBJECT))
    val FIELD: ClassName = ClassName.get(java.lang.reflect.Field::class.java)
    val METHOD_HANDLE: ClassName = ClassName.get(java.lang.invoke.MethodHandle::class.java)
}
