plugins {
    `maven-publish`
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
                        url.set("https://github.com/zlataovce/takenaka/blob/main/LICENSE")
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
                    url.set("https://github.com/zlataovce/takenaka/tree/main")
                }
            }
        }
    }
}
