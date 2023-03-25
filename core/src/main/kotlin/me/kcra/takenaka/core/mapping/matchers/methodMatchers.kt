package me.kcra.takenaka.core.mapping.matchers

import net.fabricmc.mappingio.tree.MappingTreeView

/**
 * Is this method a constructor?
 */
inline val MappingTreeView.MethodMappingView.isConstructor: Boolean
    get() = srcName == "<init>"

/**
 * Is this method a static initializer?
 */
inline val MappingTreeView.MethodMappingView.isStaticInitializer: Boolean
    get() = srcName == "<clinit>"

/**
 * Is this method an [Object.equals] method?
 */
inline val MappingTreeView.MethodMappingView.isEquals: Boolean
    get() = srcName == "equals" && srcDesc == "(Ljava/lang/Object;)Z"

/**
 * Is this method an [Object.toString] method?
 */
inline val MappingTreeView.MethodMappingView.isToString: Boolean
    get() = srcName == "toString" && srcDesc == "()Ljava/lang/String;"

/**
 * Is this method an [Object.hashCode] method?
 */
inline val MappingTreeView.MethodMappingView.isHashCode: Boolean
    get() = srcName == "hashCode" && srcDesc == "()I"

/**
 * Is this method a `valueOf` method of an enum?
 */
inline val MappingTreeView.MethodMappingView.isEnumValueOf: Boolean
    get() = srcName == "valueOf" && srcDesc.startsWith("(Ljava/lang/String;)")

/**
 * Is this method a `values` method of an enum?
 */
inline val MappingTreeView.MethodMappingView.isEnumValues: Boolean
    get() = srcName == "values" && srcDesc.startsWith("()[")
