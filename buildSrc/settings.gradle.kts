dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

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

rootProject.name = "buildSrc"
