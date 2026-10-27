import lokki.buildlogic.abiCheckEnabled
import lokki.buildlogic.targetJvm
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        targetJvm(compilerOptions)
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
