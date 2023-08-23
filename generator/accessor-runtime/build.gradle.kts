plugins {
    id("takenaka.base-conventions")
    id("takenaka.publish-conventions")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    compileOnly(libs.bundles.kotlin)
    compileOnlyApi(libs.jb.annotations)
}
