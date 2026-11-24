plugins {
    id("lokki.kotlin-jvm")
    id("lokki.publish")
}

description = "Spring test integration for lokki: @MockBean and @MockSpyBean replace beans with lokki mocks and spies"

kotlin {
    compilerOptions {
        optIn.add("io.lokki.InternalLokkiApi")
    }
}

dependencies {
    api(project(":lokki-core"))
    compileOnly(libs.spring.test)
    compileOnly(libs.spring.context)
}
