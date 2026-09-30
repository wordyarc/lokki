plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "lokki"

listOf(
    "lokki-compiler-plugin",
    "lokki-gradle-plugin",
    "lokki-plugin-annotations",
).forEach { module ->
    include(module)
    project(":$module").projectDir = file("modules/$module")
}
