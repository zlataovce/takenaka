import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.neoforged.net/releases")
}

dependencies {
    implementation(libs.build.licenser)
    implementation(libs.build.gradle.versions)
    implementation(libs.build.kotlin.jvm)
}

dependencies {
    // https://github.com/gradle/gradle/issues/15383
    compileOnly(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
