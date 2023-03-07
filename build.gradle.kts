plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "me.kcra.takenaka"
    version = "0.0.1-SNAPSHOT"
}
