import lokki.buildlogic.checkLatestCompilerApi
import lokki.buildlogic.compileAgainstCompilerApi
import lokki.buildlogic.compilerAdapters
import lokki.buildlogic.compilerApiBaseline

plugins {
    id("lokki.compiler-api")
}

description = "Contract between the lokki compiler plugin and its adapters to the compiler API of each Kotlin release"

compileAgainstCompilerApi(compilerApiBaseline)
checkLatestCompilerApi()

java {
    withSourcesJar()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:$compilerApiBaseline")
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlin.tooling.core)
}

tasks.test {
    systemProperty("lokki.compiler.minimums", compilerAdapters.joinToString(",") { it.minVersion })
}
