import lokki.buildlogic.abiCheckEnabled
import lokki.buildlogic.kotlinModuleName
import lokki.buildlogic.targetJvm
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    targetJvm(compilerOptions)

    if (abiCheckEnabled) {
        explicitApi()

        @OptIn(ExperimentalAbiValidation::class)
        abiValidation()
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.moduleName = kotlinModuleName
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
