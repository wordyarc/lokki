plugins {
    id("lokki.kotlin-jvm")
    id("lokki.publish")
}

dependencies {
    api(project(":lokki-core"))
    api(libs.junit.jupiter.api)
}

kotlin {
    compilerOptions {
        optIn.add("io.lokki.InternalLokkiApi")
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
