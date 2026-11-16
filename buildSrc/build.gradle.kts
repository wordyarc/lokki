plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.tooling.core)
    implementation(libs.buildconfig.gradle.plugin)
}
