plugins {
    id("org.cadixdev.licenser")
    `maven-publish`
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

license {
    header(rootProject.file("license_header.txt"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("takenaka")
                description.set("A Kotlin library for comparing obfuscation mappings of Minecraft: Java Edition.")
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
