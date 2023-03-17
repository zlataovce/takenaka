import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.kotlin.plugin.allopen)
}

apply(plugin = "org.jetbrains.kotlin.jvm")

sourceSets {
    create("benchmark") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val benchmarkImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    api(libs.bundles.asm)
    api(libs.bundles.jackson)
    api(libs.mapping.io)
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlin.logging.jvm)
    testImplementation(libs.kotlinx.coroutines.core.jvm)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.slf4j.simple)
    benchmarkImplementation(libs.kotlinx.benchmark.runtime)
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}
