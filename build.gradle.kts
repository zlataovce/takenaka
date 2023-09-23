plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "me.kcra.takenaka"
    version = "1.0.2-SNAPSHOT"
}
