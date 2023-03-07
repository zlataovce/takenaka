plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.shadow)
    application
}

apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    implementation(project(":generator-common-cli"))
    implementation(project(":generator-web"))
}

application {
    mainClass.set("me.kcra.takenaka.generator.common.cli.Main")
}

tasks.withType<JavaExec> {
    System.getProperties().forEach { k, v ->
        systemProperty(k.toString(), v)
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "me.kcra.takenaka.generator.common.cli.Main"
    }
}
