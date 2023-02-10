plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    implementation(project(":generator-common"))
    implementation(libs.kotlinx.html.jvm)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
