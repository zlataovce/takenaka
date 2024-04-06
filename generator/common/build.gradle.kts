plugins {
    id("takenaka.base-conventions")
    id("takenaka.kotlin-conventions")
    id("takenaka.publish-conventions")
}

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
