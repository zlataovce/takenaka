plugins {
    id("org.cadixdev.licenser")
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}
