plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.shadow)
    application
}

apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    implementation(project(":generator-web"))
    implementation(libs.kotlinx.cli.jvm)
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("me.kcra.takenaka.generator.web.cli.Main")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "me.kcra.takenaka.generator.web.cli.Main"
    }
}
