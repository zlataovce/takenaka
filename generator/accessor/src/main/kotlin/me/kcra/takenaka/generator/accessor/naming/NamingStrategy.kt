package me.kcra.takenaka.generator.accessor.naming

/**
 * A strategy for naming generated classes and its members.
 */
interface NamingStrategy {
    /**
     * Creates a new name for the specified class and the generated class type.
     *
     * @param className fully qualified name of the mapped class
     * @param classType type of the class being generated (Mapping, Accessor, ...).
     *          The naming strategy has to count with this information to not create conflicting names.
     * @return A partially qualified name of the resulting class to which a user-specified base-package will be prepended.
     */
    fun klass(className: String, classType: GeneratedClassType): String

    /**
     * Creates a new name for a field representing constructor accessor.
     *
     * @param index index of the constructor
     * @return name for a field representing the indexed constructor
     */
    fun constructor(index: Int): String

    /**
     * Creates a new name for a field representing field accessor.
     *
     * @param fieldName name of the requested field
     * @param index index of the requested field among fields with the same name (starts at 0)
     * @param constantAccessor whether a constant accessor (object supplier) is being generated for the field
     * @return name for a field representing the field
     */
    fun field(fieldName: String, index: Int, constantAccessor: Boolean): String

    /**
     * Creates a new name for a field representing method accessor.
     *
     * @param methodName name of the requested method
     * @param index index of the requested method among methods with the same name (starts at 0)
     * @return name for a field representing the method
     */
    fun method(methodName: String, index: Int): String
}