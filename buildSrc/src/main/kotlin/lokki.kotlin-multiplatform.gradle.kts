import lokki.buildlogic.abiCheckEnabled
import lokki.buildlogic.targetJvm
import lokki.buildlogic.version
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        targetJvm(compilerOptions, version("jvm-target-runtime"))
    }

    if (abiCheckEnabled) {
        explicitApi()

        @OptIn(ExperimentalAbiValidation::class)
        abiValidation()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
