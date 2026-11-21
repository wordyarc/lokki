import lokki.buildlogic.COMPILER_PLUGIN_ID
import lokki.buildlogic.DEBUG_OPTION
import lokki.buildlogic.MOCKABLE_OPTION
import lokki.buildlogic.LocationSystemProperty
import lokki.buildlogic.REPOSITORY_URL
import lokki.buildlogic.compilerApiBaseline
import lokki.buildlogic.functionalTestRepository
import lokki.buildlogic.latestTestedKotlin
import lokki.buildlogic.testedKotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("lokki.kotlin-jvm")
    id("lokki.publish")
    id("com.gradle.plugin-publish")
    `jvm-test-suite`
    id("com.github.gmazzo.buildconfig")
}

description = "Gradle plugin that applies the lokki compiler plugin to test compilations and adds lokki-core"

val minGradleVersion = libs.versions.gradle.min.version.get()
val minGradleKotlinVersion = KotlinVersion.fromVersion(libs.versions.gradle.min.kotlin.get())

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)
}

kotlin {
    compilerOptions {
        apiVersion = minGradleKotlinVersion
        languageVersion = minGradleKotlinVersion
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named(minGradleVersion))
        }
    }
}

buildConfig {
    packageName("io.lokki.gradle")
    buildConfigField("COMPILER_PLUGIN_ID", COMPILER_PLUGIN_ID)
    buildConfigField("MOCKABLE_OPTION", MOCKABLE_OPTION)
    buildConfigField("DEBUG_OPTION", DEBUG_OPTION)
    buildConfigField("GROUP", group.toString())
    buildConfigField("VERSION", version.toString())
    buildConfigField("MIN_KOTLIN_VERSION", compilerApiBaseline)
    buildConfigField("LATEST_TESTED_KOTLIN", latestTestedKotlin)
}

gradlePlugin {
    website = REPOSITORY_URL
    vcsUrl = REPOSITORY_URL
    plugins {
        create("lokki") {
            id = "io.lokki"
            displayName = "lokki"
            description = project.description
            implementationClass = "io.lokki.gradle.LokkiGradleSubplugin"
            tags = listOf("kotlin", "testing", "mocking", "compiler-plugin")
        }
    }
}

val publishedProjects = listOf(project, project(":lokki-compiler-plugin"), project(":lokki-core"))
val functionalTestRepositories = publishedProjects.map { it.functionalTestRepository }
val minGradleJavaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(libs.versions.jvm.target.get())
}
val androidSdk = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
    .orElse(providers.systemProperty("user.home").map { "$it/Library/Android/sdk" })

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.jupiter)

            dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(libs.kotlin.tooling.core)
            }
        }

        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junit.jupiter)

            dependencies {
                implementation(gradleTestKit())
                implementation(libs.kotlin.test.junit5)
                publishedProjects.forEach { runtimeOnly(project(it.path)) }
            }

            targets.configureEach {
                testTask.configure {
                    publishedProjects.forEach {
                        dependsOn("${it.path}:publishAllPublicationsToFunctionalTestRepository")
                    }
                    jvmArgumentProviders.add(
                        LocationSystemProperty(
                            "lokki.test.repositories",
                            provider {
                                functionalTestRepositories.joinToString(File.pathSeparator) { it.get().asFile.path }
                            },
                        )
                    )
                    jvmArgumentProviders.add(
                        LocationSystemProperty(
                            "lokki.test.minGradleJavaHome",
                            minGradleJavaLauncher.map { it.metadata.installationPath.asFile.path },
                        )
                    )
                    jvmArgumentProviders.add(LocationSystemProperty("lokki.test.androidSdk", androidSdk))
                    jvmArgumentProviders.add(
                        LocationSystemProperty(
                            "org.gradle.testkit.dir",
                            layout.buildDirectory.dir("test-kit").map { it.asFile.path },
                        )
                    )
                    providers.gradleProperty("lokki.test.gradle").orNull?.let { systemProperty("lokki.test.gradle", it) }
                    systemProperty("lokki.test.minGradleVersion", minGradleVersion)
                    systemProperty("lokki.test.minAgpVersion", libs.versions.agp.min.get())
                    systemProperty("lokki.test.agpVersion", libs.versions.agp.latest.get())
                    systemProperty("lokki.test.lokkiGroup", project.group.toString())
                    systemProperty("lokki.test.lokkiVersion", version.toString())
                    systemProperty("lokki.test.kotlinVersion", libs.versions.kotlin.get())
                    systemProperty("lokki.test.minKotlinVersion", compilerApiBaseline)
                    systemProperty("lokki.test.latestTestedKotlin", latestTestedKotlin)
                    systemProperty("lokki.test.testedKotlin", testedKotlin.joinToString(","))
                }
            }
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("functionalTest"))
}
