plugins {
    id("takenaka.base-conventions")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    api(libs.bundles.asm)
    api(libs.bundles.jackson)
    api(libs.mapping.io)
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlin.logging.jvm)
    testImplementation(libs.kotlinx.coroutines.core.jvm)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
