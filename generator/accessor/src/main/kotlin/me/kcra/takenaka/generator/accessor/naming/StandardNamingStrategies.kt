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

package me.kcra.takenaka.generator.accessor.naming

import me.kcra.takenaka.generator.accessor.GeneratedClassType
import me.kcra.takenaka.generator.accessor.model.ClassAccessor

/**
 * Standard [NamingStrategy] implementations.
 *
 * @author Michal Turek
 * @author Matouš Kučera
 */
object StandardNamingStrategies {
    /**
     * A simple, package-less strategy.
     *
     * **Does not account for conflicts for classes with the same simple name,
     * but different package; use [resolveSimpleConflicts].**
     */
    val SIMPLE: NamingStrategy = SimpleNamingStrategy()

    /**
     * A fully-qualified class name strategy, based on [SIMPLE].
     *
     * Uses the entire mapped name, including the full package.
     */
    val FULLY_QUALIFIED: NamingStrategy = FullyQualifiedNamingStrategy()

    /**
     * A semi-qualified class name strategy, based on [SIMPLE].
     *
     * Uses the entire mapped name, including the package, but with `net.minecraft`/`com.mojang` removed.
     */
    val SEMI_QUALIFIED: NamingStrategy = SemiQualifiedNamingStrategy()
}

/**
 * Prefixes (simple) class names with a package.
 *
 * @param basePackage the package
 */
fun NamingStrategy.prefixed(basePackage: String): NamingStrategy {
    if (basePackage.isEmpty()) {
        return this
    }

    return PrefixingNamingStrategy(this, basePackage)
}

/**
 * Resolves simple class name conflicts by suffixing them with an occurrence index.
 *
 * *Zero indexes are skipped, as they point to the original class - conflicting classes start numbering at 1.*
 */
fun NamingStrategy.resolveSimpleConflicts(): NamingStrategy {
    if (this is SimpleConflictResolvingNamingStrategy) {
        return this
    }

    return SimpleConflictResolvingNamingStrategy(this)
}

private class PrefixingNamingStrategy(next: NamingStrategy, val basePackage: String) : ForwardingNamingStrategy(next) {
    override fun klass(model: ClassAccessor, type: GeneratedClassType): String {
        return "$basePackage.${super.klass(model, type)}"
    }

    override fun klass(type: GeneratedClassType): String {
        return "$basePackage.${super.klass(type)}"
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

private data class TypedName(val name: String, val type: GeneratedClassType)

private class SimpleConflictResolvingNamingStrategy(next: NamingStrategy) : ForwardingNamingStrategy(next) {
    @Transient
    private val simpleNames = mutableMapOf<TypedName, MutableMap<String, Int>>() // {simple name + type: {fq name: occurrence index}}

    override fun klass(model: ClassAccessor, type: GeneratedClassType): String {
        val simpleName = model.internalName.substringAfterLast('/')
        val index = synchronized(simpleNames) {
            val names = simpleNames.getOrPut(TypedName(simpleName, type), ::mutableMapOf)

            names.getOrPut(model.internalName) { names.size }
        }

        if (index > 0) { // copy accessor with a unique name
            return super.klass(ClassAccessor(model.name + index, model.fields, model.constructors, model.methods, model.requiredTypes), type)
        }
        return super.klass(model, type)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
