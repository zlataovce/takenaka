package me.kcra.takenaka.generator.accessor.naming

import me.kcra.takenaka.generator.accessor.util.camelToUpperSnakeCase


enum class StandardNamingStrategies: NamingStrategy {
    SIMPLE {
        override fun klass(className: String, classType: GeneratedClassType): String {
            val index = className.lastIndexOf('.')
            return if (index != -1) {
                className.substring(index + 1)
            } else {
                className
            } + when(classType) {
                GeneratedClassType.MAPPING -> "Mapping"
                GeneratedClassType.ACCESSOR -> "Accessor"
                GeneratedClassType.EXTRA -> ""
            }
        }

        override fun constructor(index: Int): String = "CONSTRUCTOR_$index"

        override fun field(fieldName: String, index: Int, constantAccessor: Boolean): String =
            "FIELD_${fieldName.camelToUpperSnakeCase()}${index.let { if (it != 0) "_$it" else "" }}"

        override fun method(methodName: String, index: Int): String =
            "METHOD_${methodName.camelToUpperSnakeCase()}${index.let { if (it != 0) "_$it" else "" }}"
    },
    // TODO: more standard strategies
}