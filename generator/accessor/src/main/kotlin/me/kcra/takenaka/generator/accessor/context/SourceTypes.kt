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
    val NULLABLE: ClassName = ClassName.get("org.jetbrains.annotations", "Nullable")
    val CLASS: ClassName = ClassName.get(java.lang.Class::class.java)
    val CLASS_WILDCARD: ParameterizedTypeName = ParameterizedTypeName.get(CLASS, WildcardTypeName.subtypeOf(ClassName.OBJECT))
}
