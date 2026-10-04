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
    "compiler-tests",
    "lokki-compiler-plugin",
    "lokki-core",
    "lokki-gradle-plugin",
).forEach { module ->
    include(module)
    project(":$module").projectDir = file("modules/$module")
}
