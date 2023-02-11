plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

dependencies {
    implementation(project(":generator-common-cli"))
    implementation(project(":generator-web"))
}

application {
    mainClass.set("me.kcra.takenaka.generator.common.cli.Main")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "me.kcra.takenaka.generator.common.cli.Main"
    }
}
