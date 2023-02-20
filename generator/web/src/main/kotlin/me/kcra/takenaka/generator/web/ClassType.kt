package me.kcra.takenaka.generator.web

import org.objectweb.asm.Opcodes
import java.util.*

/**
 * A class type.
 *
 * @author Matouš Kučera
 */
enum class ClassType {
    CLASS, INTERFACE, ENUM;

    override fun toString(): String = name.lowercase()
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/**
 * Makes a class type from a modifier.
 *
 * @param mod the modifier
 * @return the type
 */
fun classTypeOf(mod: Int): ClassType = when {
    (mod and Opcodes.ACC_INTERFACE) != 0 -> ClassType.INTERFACE
    (mod and Opcodes.ACC_ENUM) != 0 -> ClassType.ENUM
    else -> ClassType.CLASS
}
