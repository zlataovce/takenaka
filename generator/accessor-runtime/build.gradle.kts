plugins {
    id("takenaka.base-conventions")
    id("takenaka.publish-conventions")
    alias(libs.plugins.lombok)
}

dependencies {
    compileOnlyApi(libs.jb.annotations)
}
