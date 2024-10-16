import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("net.neoforged.licenser")
    id("com.github.ben-manes.versions")
    `maven-publish`
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

license {
    header(rootProject.file("license_header.txt"))

    exclude("**/*.properties") // detection is not very good, doesn't need a license anyway
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

publishing {
    repositories {
        maven {
            url = uri(
                if ((project.version as String).endsWith("-SNAPSHOT")) {
                    "https://repo.screamingsandals.org/snapshots"
                } else {
                    "https://repo.screamingsandals.org/releases"
                }
            )
            credentials {
                username = System.getenv("REPO_USERNAME")
                password = System.getenv("REPO_PASSWORD")
            }
        }
    }
}
