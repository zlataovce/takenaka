plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":generator-common"))
    api(libs.kotlinx.html.jvm)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
