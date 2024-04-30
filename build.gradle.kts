plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "me.kcra.takenaka"
    version = "1.1.3-SNAPSHOT"
    description = "A Kotlin library for reconciling multiple obfuscation mapping files from multiple versions of Minecraft: JE."
}
