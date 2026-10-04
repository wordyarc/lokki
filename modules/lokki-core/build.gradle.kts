plugins {
    id("lokki.kotlin-multiplatform")
    id("lokki.publish")
}

kotlin {
    compilerOptions {
        optIn.add("io.lokki.InternalLokkiApi")
    }
}
