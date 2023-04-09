plugins {
    id("takenaka.base-conventions")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    api(project(":generator-common"))
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
