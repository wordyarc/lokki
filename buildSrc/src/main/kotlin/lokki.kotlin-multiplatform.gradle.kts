import lokki.buildlogic.abiCheckEnabled
import lokki.buildlogic.kotlinModuleName
import lokki.buildlogic.targetJvm
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

tasks.named<KotlinCompile>("compileKotlinJvm") {
    compilerOptions.moduleName = kotlinModuleName
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
