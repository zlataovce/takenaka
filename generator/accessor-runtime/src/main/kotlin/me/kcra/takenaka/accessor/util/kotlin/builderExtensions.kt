package me.kcra.takenaka.accessor.util.kotlin

import me.kcra.takenaka.accessor.mapping.ClassMapping
import me.kcra.takenaka.accessor.mapping.ConstructorMapping
import me.kcra.takenaka.accessor.mapping.FieldMapping
import me.kcra.takenaka.accessor.mapping.MappingLookup
import me.kcra.takenaka.accessor.mapping.MethodMapping

inline fun mappingLookup(block: MappingLookup.() -> Unit): MappingLookup = MappingLookup().apply(block)

inline fun classMapping(name: String, block: ClassMapping.() -> Unit): ClassMapping = ClassMapping(name).apply(block)
inline fun ClassMapping.field(name: String, block: FieldMapping.() -> Unit): FieldMapping = putField(name).apply(block)
inline fun ClassMapping.constructor(block: ConstructorMapping.() -> Unit): ConstructorMapping = putConstructor().apply(block)
inline fun ClassMapping.method(name: String, block: MethodMapping.() -> Unit): MethodMapping = putMethod(name).apply(block)
