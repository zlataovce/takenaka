import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("org.cadixdev.licenser")
    id("com.github.ben-manes.versions")
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
