import lokki.buildlogic.COMPILER_COMPAT_PROJECT
import lokki.buildlogic.COMPILER_COMPAT_SERVICE
import lokki.buildlogic.COMPILER_PLUGIN_ID
import lokki.buildlogic.ClasspathSystemProperty
import lokki.buildlogic.DEBUG_OPTION
import lokki.buildlogic.FILE_REDIRECT_SUFFIX
import lokki.buildlogic.LOKKI_ABI_FLAGS
import lokki.buildlogic.LOKKI_ABI_VERSION
import lokki.buildlogic.PRIVATE_HOOK_INFIX
import lokki.buildlogic.INTERCEPTABLE_OPTION
import lokki.buildlogic.MOCKABLE_OPTION
import lokki.buildlogic.RESTRICTED_OPTION
import lokki.buildlogic.MergeCompilerCompatServices
import lokki.buildlogic.checkLatestCompilerApi
import lokki.buildlogic.compileAgainstCompilerApi
import lokki.buildlogic.compilerAdapters
import lokki.buildlogic.compilerApiBaseline
import lokki.buildlogic.latestTestedKotlin
import lokki.buildlogic.testedKotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("lokki.compiler-api")
    id("lokki.publish")
    id("com.github.gmazzo.buildconfig")
    `jvm-test-suite`
}

description = "lokki compiler plugin: generates mock classes at compile time and lowers the mocking DSL into calls " +
    "of lokki-core"

compileAgainstCompilerApi(compilerApiBaseline)
val latestCompilerApi = checkLatestCompilerApi()

tasks.named<KotlinCompile>(latestCompilerApi.getCompileTaskName("kotlin")) {
    compilerOptions.optIn.add("org.jetbrains.kotlin.config.MessageCollectorAccess")
}

val adapters = compilerAdapters

val embeddedCompat = configurations.dependencyScope("embeddedCompat")
val embeddedCompatClasspath = configurations.resolvable("embeddedCompatClasspath") {
    extendsFrom(embeddedCompat.get())
    isTransitive = false
}
val embeddedCompatSources = configurations.resolvable("embeddedCompatSources") {
    extendsFrom(embeddedCompat.get())
    isTransitive = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

dependencies {
    compileOnly(project(COMPILER_COMPAT_PROJECT))
    latestCompilerApi.compileOnlyConfigurationName(project(COMPILER_COMPAT_PROJECT))
    embeddedCompat(project(COMPILER_COMPAT_PROJECT))
    adapters.forEach { embeddedCompat(project(it.projectPath)) }
}

buildConfig {
    packageName("io.lokki.compiler")
    buildConfigField("COMPILER_PLUGIN_ID", COMPILER_PLUGIN_ID)
    buildConfigField("MOCKABLE_OPTION", MOCKABLE_OPTION)
    buildConfigField("INTERCEPTABLE_OPTION", INTERCEPTABLE_OPTION)
    buildConfigField("RESTRICTED_OPTION", RESTRICTED_OPTION)
    buildConfigField("DEBUG_OPTION", DEBUG_OPTION)
    buildConfigField("LOKKI_ABI_VERSION", LOKKI_ABI_VERSION)
    buildConfigField("FILE_REDIRECT_SUFFIX", FILE_REDIRECT_SUFFIX)
    buildConfigField("PRIVATE_HOOK_INFIX", PRIVATE_HOOK_INFIX)
    LOKKI_ABI_FLAGS.forEach { (name, flag) -> buildConfigField(name, flag) }
    buildConfigField("VERSION", version.toString())
    buildConfigField("LATEST_TESTED_KOTLIN", latestTestedKotlin)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

val embeddedAdapters = embeddedCompatClasspath.get().incoming.artifactView {
    componentFilter { it is ProjectComponentIdentifier && it.projectPath != COMPILER_COMPAT_PROJECT }
}.files

val mergeCompilerCompatServices = tasks.register<MergeCompilerCompatServices>("mergeCompilerCompatServices") {
    adapterJars.from(embeddedAdapters)
    service = COMPILER_COMPAT_SERVICE
    expectedProviders = adapters.map { it.factoryClassName }
    outputDirectory = layout.buildDirectory.dir("generated/compat/services")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(embeddedCompatClasspath.map { jars -> jars.map(::zipTree) }) {
        exclude(COMPILER_COMPAT_SERVICE, "META-INF/MANIFEST.MF")
    }
    from(mergeCompilerCompatServices)
}

tasks.withType<Jar>().matching { it.name == "sourcesJar" }.configureEach {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    dependsOn(embeddedCompatSources)
    from(embeddedCompatSources.map { jars -> jars.map(::zipTree) }) {
        exclude("META-INF/**")
    }
}

val compilerHosts = (testedKotlin + adapters.map { it.minVersion }).distinct().associateWith { version ->
    configurations.detachedConfiguration(dependencies.create("org.jetbrains.kotlin:kotlin-compiler:$version"))
}
val embeddableCompilerHost = configurations.detachedConfiguration(
    dependencies.create("org.jetbrains.kotlin:kotlin-compiler-embeddable:$latestTestedKotlin")
)

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.jupiter)

            dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(project(":lokki-core"))
                implementation("org.jetbrains.kotlin:kotlin-compiler:$compilerApiBaseline")
            }

            targets.configureEach {
                testTask.configure {
                    description = "Checks the names of lokki-core that the compiler plugin relies on"
                }
            }
        }

        register<JvmTestSuite>("jarTest") {
            useJUnitJupiter(libs.versions.junit.jupiter)

            dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(libs.junit.jupiter.params)
                implementation(libs.asm)
                implementation(libs.kotlin.tooling.core)
            }

            targets.configureEach {
                testTask.configure {
                    description = "Checks the published jar: its contents, its dependencies and its references into " +
                        "every tested Kotlin compiler"
                    systemProperty("lokki.compiler.adapters", adapters.joinToString(","))
                    systemProperty("lokki.kotlin.tested", testedKotlin.joinToString(","))
                    jvmArgumentProviders.add(ClasspathSystemProperty("lokki.compiler.jar", files(tasks.jar)))
                    val pom = files(tasks.named("generatePomFileForMavenPublication"))
                    jvmArgumentProviders.add(ClasspathSystemProperty("lokki.compiler.pom", pom))
                    val module = files(tasks.named("generateMetadataFileForMavenPublication"))
                    jvmArgumentProviders.add(ClasspathSystemProperty("lokki.compiler.module", module))
                    compilerHosts.forEach { (version, host) ->
                        jvmArgumentProviders.add(ClasspathSystemProperty("lokki.compiler.host.$version", host))
                    }
                    jvmArgumentProviders.add(
                        ClasspathSystemProperty("lokki.compiler.embeddable", embeddableCompilerHost)
                    )
                }
            }
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("jarTest"))
}
