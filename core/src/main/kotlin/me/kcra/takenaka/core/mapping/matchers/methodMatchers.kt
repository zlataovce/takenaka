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
