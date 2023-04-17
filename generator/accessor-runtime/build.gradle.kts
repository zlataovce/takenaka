plugins {
    id("takenaka.base-conventions")
    alias(libs.plugins.lombok)
}

dependencies {
    compileOnlyApi(libs.jb.annotations)
}
