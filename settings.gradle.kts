pluginManagement {
    includeBuild("build-logic")
}

fun includeComposite(name: String, vararg modules: String) {
    modules.forEach { module ->
        include(":$name-$module")
        project(":$name-$module").projectDir = file("$name/$module")
    }
}

rootProject.name = "takenaka"

include("core")
includeComposite("generator", "common", "web", "common-cli", "web-cli")
