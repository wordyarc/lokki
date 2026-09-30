import lokki.buildlogic.targetJvm

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    targetJvm(compilerOptions)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
