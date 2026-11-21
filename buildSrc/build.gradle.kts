plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.tooling.core)
    implementation(libs.buildconfig.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.plugin.publish.gradle.plugin)
}
