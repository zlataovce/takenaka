import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// https://github.com/gradle/gradle/issues/15383
//plugins {
//    alias(libs.plugin.kotlin.jvm)
//}

apply(plugin = "org.jetbrains.kotlin.jvm")

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
    }
}