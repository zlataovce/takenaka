plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    maven("https://maven.fabricmc.net")
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.asm)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.mapping.io)
    implementation(libs.kotlin.logging.jvm)
}
