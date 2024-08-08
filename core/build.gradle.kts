plugins {
    id("takenaka.base-conventions")
    id("takenaka.kotlin-conventions")
    id("takenaka.publish-conventions")
}

dependencies {
    api(libs.bundles.asm)
    api(libs.bundles.jackson)
    api(libs.mapping.io)
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlinx.coroutines.core.jvm)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
