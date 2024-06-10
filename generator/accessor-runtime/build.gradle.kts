plugins {
    id("takenaka.base-conventions")
    id("takenaka.kotlin-conventions")
    id("takenaka.publish-conventions")
}

dependencies {
    compileOnly(libs.bundles.kotlin)
    compileOnlyApi(libs.jb.annotations)
}
