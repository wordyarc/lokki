import lokki.buildlogic.abiCheckEnabled
import lokki.buildlogic.targetJvm
import lokki.buildlogic.version
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    targetJvm(compilerOptions, version("jvm-target-tooling"))

    if (abiCheckEnabled) {
        explicitApi()

        @OptIn(ExperimentalAbiValidation::class)
        abiValidation()
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
