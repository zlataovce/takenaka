@file:OptIn(KotlinPoetJavaPoetPreview::class)

package me.kcra.takenaka.generator.accessor.context

import com.squareup.javapoet.ClassName
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import com.squareup.kotlinpoet.javapoet.toKClassName

/**
 * JavaPoet/KotlinPoet types.
 */
object SourceTypes {
    val CLASS_MAPPING: ClassName = ClassName.get("me.kcra.takenaka.accessor.mapping", "ClassMapping")
    val KT_CLASS_MAPPING = CLASS_MAPPING.toKClassName()
}
