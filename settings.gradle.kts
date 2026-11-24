plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    providers.gradleProperty("lokki.kotlin").orNull?.let { kotlin ->
        versionCatalogs {
            create("libs") {
                version("kotlin", kotlin)
            }
        }
    }
}

rootProject.name = "lokki"

listOf(
    "compiler-tests",
    "lokki-bdd",
    "lokki-compiler-compat",
    "lokki-compiler-plugin",
    "lokki-core",
    "lokki-gradle-plugin",
    "lokki-maven",
    "lokki-spring",
).forEach { module ->
    include(module)
    project(":$module").projectDir = file("modules/$module")
}

file("modules/lokki-compiler-compat").listFiles { adapter -> adapter.isDirectory && adapter.name.startsWith("kotlin-") }
    .orEmpty()
    .sortedBy { it.name }
    .forEach { adapter ->
        include(":lokki-compiler-compat:${adapter.name}")
        project(":lokki-compiler-compat:${adapter.name}").projectDir = adapter
    }
