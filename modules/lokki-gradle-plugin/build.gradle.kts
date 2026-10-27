import lokki.buildlogic.COMPILER_PLUGIN_ID
import lokki.buildlogic.MOCKABLE_OPTION
import lokki.buildlogic.LocationSystemProperty
import lokki.buildlogic.functionalTestRepository
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("lokki.kotlin-jvm")
    id("lokki.publish")
    `java-gradle-plugin`
    `jvm-test-suite`
    id("com.github.gmazzo.buildconfig")
}

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
    buildConfigField("GROUP", group.toString())
    buildConfigField("VERSION", version.toString())
}

gradlePlugin {
    plugins {
        create("lokki") {
            id = "io.lokki"
            implementationClass = "io.lokki.gradle.LokkiGradleSubplugin"
        }
    }
}

val publishedProjects = listOf(project, project(":lokki-compiler-plugin"), project(":lokki-core"))
val functionalTestRepositories = publishedProjects.map { it.functionalTestRepository }
val minGradleJavaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(libs.versions.jvm.target.get())
}

testing {
    suites {
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
                    systemProperty("lokki.test.minGradleVersion", minGradleVersion)
                    systemProperty("lokki.test.lokkiVersion", version.toString())
                    systemProperty("lokki.test.kotlinVersion", libs.versions.kotlin.get())
                }
            }
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("functionalTest"))
}
