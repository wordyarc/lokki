plugins {
    id("lokki.kotlin-multiplatform")
    id("lokki.publish")
}

description = "BDD-style aliases for the lokki DSL: given, coGiven, then and coThen"

kotlin {
    compilerOptions {
        optIn.add("io.lokki.InternalLokkiApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":lokki-core"))
        }
    }
}
