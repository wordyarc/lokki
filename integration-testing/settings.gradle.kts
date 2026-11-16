pluginManagement {
    includeBuild("..")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
            providers.gradleProperty("lokki.kotlin").orNull?.let { version("kotlin", it) }
        }
    }
}

includeBuild("..")

rootProject.name = "integration-testing"
