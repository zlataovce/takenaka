package me.kcra.takenaka.generator.web.pages

import org.objectweb.asm.Opcodes

fun formatModifiers(mod: Int, mask: Int): String = buildString {
    val mMod = mod and mask

    if ((mMod and Opcodes.ACC_PUBLIC) != 0) append("public ")
    if ((mMod and Opcodes.ACC_PRIVATE) != 0) append("private ")
    if ((mMod and Opcodes.ACC_PROTECTED) != 0) append("protected ")
    if ((mMod and Opcodes.ACC_STATIC) != 0) append("static ")
    // an interface is implicitly abstract
    // we need to check the unmasked modifiers here, since ACC_INTERFACE is not among Modifier#classModifiers
    if ((mMod and Opcodes.ACC_ABSTRACT) != 0 && (mod and Opcodes.ACC_INTERFACE) == 0) append("abstract ")
    if ((mMod and Opcodes.ACC_FINAL) != 0) append("final ")
    if ((mMod and Opcodes.ACC_NATIVE) != 0) append("native ")
    if ((mMod and Opcodes.ACC_STRICT) != 0) append("strict ")
    if ((mMod and Opcodes.ACC_SYNCHRONIZED) != 0) append("synchronized ")
    if ((mMod and Opcodes.ACC_TRANSIENT) != 0) append("transient ")
    if ((mMod and Opcodes.ACC_VOLATILE) != 0) append("volatile ")
}
