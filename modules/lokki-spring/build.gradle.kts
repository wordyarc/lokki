import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("lokki.kotlin-jvm")
    id("lokki.publish")
}

description = "Spring test integration for lokki: @MockBean and @MockSpyBean replace beans with lokki mocks and spies"

mavenPublishing {
    configure(KotlinJvm(JavadocJar.Empty()))
}

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
