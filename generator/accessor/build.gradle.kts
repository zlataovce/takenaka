plugins {
    id("takenaka.base-conventions")
    id("takenaka.kotlin-conventions")
    id("takenaka.publish-conventions")
}

dependencies {
    api(project(":generator-common"))
    implementation(libs.javapoet)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.javapoet)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
