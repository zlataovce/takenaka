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
    implementation(libs.bundles.jackson)
    implementation(libs.mapping.io)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlinx.coroutines.core.jvm)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
