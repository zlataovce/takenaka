@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.shadow)
    alias(libs.plugins.build.config)
    `kotlin-dsl`
}

// https://imperceptiblethoughts.com/shadow/plugins/#special-handling-of-the-java-gradle-plugin-development-plugin
configurations.api.configure {
    dependencies.remove(project.dependencies.gradleApi())
}

dependencies {
    compileOnlyApi(gradleApi())
    implementation(project(":generator-accessor")) {
        exclude(group = "org.jetbrains.kotlin") // handled by gradle
    }
}

gradlePlugin {
    website.set("https://github.com/zlataovce/takenaka")
    vcsUrl.set("https://github.com/zlataovce/takenaka.git")

    plugins {
        create("generator-accessor-plugin") {
            id = "me.kcra.takenaka.accessor"
            displayName = "Plugin for generating Minecraft: JE reflective accessors"
            description = "A plugin for generating reflective bindings to access Minecraft internals"
            tags.set(listOf("minecraft", "obfuscation", "accessors", "reflection"))
            implementationClass = "me.kcra.takenaka.generator.accessor.plugin.AccessorGeneratorPlugin"
        }
    }
}

buildConfig {
    packageName("me.kcra.takenaka.gradle")
    useKotlinOutput()

    buildConfigField("String", "BUILD_VERSION", "\"$version\"")
    buildConfigField("String", "BUILD_MAVEN_GROUP", "\"$group\"")
}

license {
    exclude("**/BuildConfig.kt") // generated file
}

// needed for com.gradle.plugin-publish to detect the shaded JAR
tasks.withType<ShadowJar> {
    archiveClassifier.set("")
}
