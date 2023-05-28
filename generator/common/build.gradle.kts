plugins {
    id("takenaka.base-conventions")
    id("takenaka.publish-conventions")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    api(project(":core"))
    api(libs.kotlin.logging.jvm)
    api(libs.kotlinx.coroutines.core.jvm)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
