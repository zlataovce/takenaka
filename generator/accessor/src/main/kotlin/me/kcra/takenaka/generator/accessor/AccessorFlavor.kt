package me.kcra.takenaka.generator.accessor

/**
 * Ways to access classes and their members.
 *
 * @author Matouš Kučera
 */
enum class AccessorFlavor {
    /**
     * The `java.lang.reflect` API.
     */
    REFLECTION,

    /**
     * The `java.lang.invoke` API.
     */
    METHOD_HANDLES,

    /**
     * No access code is generated, only mappings.
     */
    NONE
}
