plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":generator-common"))
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlinx.cli.jvm)
    runtimeOnly(libs.slf4j.simple)
}
