import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("org.cadixdev.licenser")
    id("com.github.ben-manes.versions")
    `maven-publish`
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net")
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

java {
    withSourcesJar()
    withJavadocJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

license {
    skipExistingHeaders(true)
    header(rootProject.file("license_header.txt"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("takenaka")
                description.set("A Kotlin library for reconciling multiple obfuscation mapping files over multiple versions of Minecraft: JE.")
                url.set("https://github.com/zlataovce/takenaka")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://github.com/zlataovce/takenaka/blob/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("zlataovce")
                        name.set("Matouš Kučera")
                        email.set("mk@kcra.me")
                    }
                }
                scm {
                    connection.set("scm:git:github.com/zlataovce/takenaka.git")
                    developerConnection.set("scm:git:ssh://github.com/zlataovce/takenaka.git")
                    url.set("https://github.com/zlataovce/takenaka/tree/master")
                }
            }
        }
    }
}

val versionRegex = "^[0-9,.v-]+(-r)?$".toRegex()
val stableKeywords = listOf("RELEASE", "FINAL", "GA")

fun isStable(version: String): Boolean {
    val normalVersion = version.uppercase()

    return stableKeywords.any(normalVersion::contains) || version.matches(versionRegex)
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isStable(currentVersion) && !isStable(candidate.version)
    }
}
