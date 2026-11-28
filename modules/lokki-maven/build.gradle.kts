import lokki.buildlogic.COMPILER_PLUGIN_ID
import lokki.buildlogic.LocationSystemProperty
import lokki.buildlogic.INTERCEPTABLE_OPTION
import lokki.buildlogic.MOCKABLE_OPTION
import lokki.buildlogic.compilerApiBaseline
import lokki.buildlogic.functionalTestRepository
import lokki.buildlogic.latestTestedKotlin
import lokki.buildlogic.testedKotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("lokki.host-stdlib")
    id("lokki.kotlin-jvm")
    id("lokki.publish")
    `jvm-test-suite`
    id("com.github.gmazzo.buildconfig")
}

description = "lokki for kotlin-maven-plugin: <compilerPlugins><plugin>lokki</plugin></compilerPlugins> applies the " +
    "lokki compiler plugin"

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly("org.jetbrains.kotlin:kotlin-maven-plugin:$compilerApiBaseline") { isTransitive = false }
    compileOnly(libs.maven.core)
    runtimeOnly(project(":lokki-compiler-plugin"))
}

kotlin {
    compilerOptions {
        apiVersion = KotlinVersion.fromVersion(compilerApiBaseline.substringBeforeLast('.'))
    }
}

buildConfig {
    packageName("io.lokki.maven")
    buildConfigField("COMPILER_PLUGIN_ID", COMPILER_PLUGIN_ID)
    buildConfigField("MOCKABLE_OPTION", MOCKABLE_OPTION)
    buildConfigField("INTERCEPTABLE_OPTION", INTERCEPTABLE_OPTION)
}

interface ArchiveAccess {
    @get:Inject
    val archives: ArchiveOperations
}

fun installMaven(name: String, version: String): TaskProvider<Sync> {
    val archives = objects.newInstance<ArchiveAccess>().archives
    val capitalized = name.replaceFirstChar(Char::uppercase)
    val distribution = configurations.detachedConfiguration(
        dependencies.create("org.apache.maven:apache-maven:$version:bin@zip"),
    )
    return tasks.register<Sync>("install${capitalized}Maven") {
        from(distribution.elements.map { files -> files.map { archives.zipTree(it.asFile) } })
        into(layout.buildDirectory.dir("maven/$version"))
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
}

val mavenInstallations = mapOf(
    "current" to installMaven("current", libs.versions.maven.latest.get()),
    "minimal" to installMaven("minimal", libs.versions.maven.min.get()),
)

val publishedProjects = listOf(project, project(":lokki-compiler-plugin"), project(":lokki-core"))
val functionalTestRepositories = publishedProjects.map { it.functionalTestRepository }
val minMavenJavaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(libs.versions.jvm.target.get())
}

testing {
    suites {
        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junit.jupiter)

            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlin.test.junit5)
                publishedProjects.forEach { runtimeOnly(project(it.path)) }
            }

            targets.configureEach {
                testTask.configure {
                    publishedProjects.forEach {
                        dependsOn("${it.path}:publishAllPublicationsToFunctionalTestRepository")
                    }
                    mavenInstallations.forEach { (name, installation) ->
                        dependsOn(installation)
                        jvmArgumentProviders.add(
                            LocationSystemProperty(
                                "lokki.test.maven.$name",
                                installation.map { it.destinationDir.path },
                            )
                        )
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
                            "lokki.test.mavenRepository",
                            layout.buildDirectory.dir("maven-repository").map { it.asFile.path },
                        )
                    )
                    jvmArgumentProviders.add(
                        LocationSystemProperty(
                            "lokki.test.minMavenJavaHome",
                            minMavenJavaLauncher.map { it.metadata.installationPath.asFile.path },
                        )
                    )
                    systemProperty("lokki.test.minMavenVersion", libs.versions.maven.min.get())
                    systemProperty("lokki.test.mavenVersion", libs.versions.maven.latest.get())
                    systemProperty("lokki.test.surefireVersion", libs.versions.maven.surefire.get())
                    systemProperty("lokki.test.jupiterVersion", libs.versions.junit.jupiter.get())
                    systemProperty("lokki.test.lokkiGroup", project.group.toString())
                    systemProperty("lokki.test.lokkiVersion", version.toString())
                    systemProperty("lokki.test.kotlinVersion", libs.versions.kotlin.get())
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
