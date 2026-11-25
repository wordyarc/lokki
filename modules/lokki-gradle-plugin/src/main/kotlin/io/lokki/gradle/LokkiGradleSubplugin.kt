package io.lokki.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class LokkiGradleSubplugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        val lokki = target.extensions.create("lokki", LokkiGradleExtension::class.java)
        lokki.recordStackTraces.convention(target.booleanProperty(RECORD_STACK_TRACES))
        target.alignLokkiVersions()
        target.checkKotlinVersion()
        target.tasks.withType(Test::class.java).configureEach {
            it.jvmArgumentProviders.add(StackTraceRecording(lokki.recordStackTraces))
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.platformType in PLATFORM_TYPES &&
            kotlinCompilation.hasDefaultSourceSet() &&
            (kotlinCompilation.isTest() || kotlinCompilation.isMain() && kotlinCompilation.lokki.appliesToMain())

    override fun getCompilerPluginId(): String = BuildConfig.COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(BuildConfig.GROUP, "lokki-compiler-plugin", BuildConfig.VERSION)

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val lokki = kotlinCompilation.lokki
        kotlinCompilation.compileTaskProvider.configure { task ->
            task.compilerOptions.freeCompilerArgs.addAll(PLUGIN_ORDER.map { "-Xcompiler-plugin-order=$it" })
        }
        val test = kotlinCompilation.isTest()
        if (test || lokki.applyToMain.get()) {
            kotlinCompilation.defaultSourceSet.dependencies {
                implementation("${BuildConfig.GROUP}:lokki-core:${BuildConfig.VERSION}")
            }
        }
        if (!test && lokki.mockable.get().isNotEmpty()) kotlinCompilation.warnAboutMockableBytecode()
        val patterns = lokki.mockable.zip(lokki.restricted) { mockable, restricted -> mockable to restricted }
        val debugging = kotlinCompilation.target.project.booleanProperty(DEBUG)
        return debugging.zip(patterns) { debug, (mockable, restricted) ->
            val opened = when {
                test -> mockable.filterNot { it.endsWith(SUBPACKAGES) }
                else -> mockable
            }.map { SubpluginOption(BuildConfig.MOCKABLE_OPTION, it) }
            val checked = if (test) restricted.map { SubpluginOption(BuildConfig.RESTRICTED_OPTION, it) } else emptyList()
            opened + checked + listOfNotNull(SubpluginOption(BuildConfig.DEBUG_OPTION, "true").takeIf { debug })
        }
    }
}

internal class StackTraceRecording(@get:Input val enabled: Provider<Boolean>) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        if (enabled.get()) listOf("-D$RECORD_STACK_TRACES=true") else emptyList()
}

private val KotlinCompilation<*>.lokki: LokkiGradleExtension
    get() = target.project.extensions.getByType(LokkiGradleExtension::class.java)

private fun LokkiGradleExtension.appliesToMain(): Boolean = applyToMain.get() || mockable.get().isNotEmpty()

private fun Project.booleanProperty(name: String): Provider<Boolean> =
    providers.gradleProperty(name).map { it.toBoolean() }.orElse(false)

private fun Project.alignLokkiVersions() {
    configurations.configureEach { configuration ->
        configuration.withDependencies { dependencies ->
            dependencies.filterIsInstance<ExternalDependency>()
                .filter { it.group == BuildConfig.GROUP && it.version.isNullOrEmpty() }
                .forEach { dependency ->
                    this.dependencies.constraints.add(configuration.name, "${dependency.group}:${dependency.name}") {
                        it.version { version -> version.require(BuildConfig.VERSION) }
                    }
                }
        }
    }
}

private fun Project.checkKotlinVersion() {
    var checked = false
    plugins.withType(KotlinBasePlugin::class.java).configureEach { kotlin ->
        if (!checked) {
            checked = true
            checkKotlinVersion(kotlin.pluginVersion)
        }
    }
}

private fun Project.checkKotlinVersion(version: String) {
    when (kotlinCompatibility(version, BuildConfig.MIN_KOTLIN_VERSION, BuildConfig.LATEST_TESTED_KOTLIN)) {
        KotlinCompatibility.SUPPORTED -> Unit
        KotlinCompatibility.OLDER_THAN_SUPPORTED -> throw GradleException(
            "lokki ${BuildConfig.VERSION} requires Kotlin ${BuildConfig.MIN_KOTLIN_VERSION} or newer, but the build " +
                "uses Kotlin $version: the lokki compiler plugin has no adapter to its compiler API."
        )
        KotlinCompatibility.NEWER_THAN_TESTED ->
            if (providers.gradleProperty(KOTLIN_COMPATIBILITY_NOWARN).orNull?.toBoolean() != true) {
                logger.warn(
                    "lokki ${BuildConfig.VERSION} is tested with Kotlin up to ${BuildConfig.LATEST_TESTED_KOTLIN}, " +
                        "but the build uses Kotlin $version: if the lokki compiler plugin fails, use Kotlin " +
                        "${BuildConfig.LATEST_TESTED_KOTLIN} or a newer lokki. To disable this warning, set " +
                        "$KOTLIN_COMPATIBILITY_NOWARN=true in gradle.properties."
                )
            }
    }
}

private fun KotlinCompilation<*>.warnAboutMockableBytecode() {
    val project = target.project
    if (project.providers.gradleProperty(MOCKABLE_PUBLICATION_NOWARN).orNull?.toBoolean() == true) return
    val task = project.absoluteProjectPath(compileTaskProvider.name)
    fun warn(outputs: String) {
        project.logger.warn(
            "lokki { mockable(...) } opens classes and adds interception points to objects and top-level " +
                "functions compiled by $task, and $outputs contain this bytecode. To disable this warning, set " +
                "$MOCKABLE_PUBLICATION_NOWARN=true in gradle.properties."
        )
    }
    when {
        !isAndroidVariant() -> project.pluginManager.withPlugin("maven-publish") {
            warn("the project applies maven-publish: its published artifacts")
        }
        !name.endsWith("debug", ignoreCase = true) -> warn("the APK or AAR of this Android variant would")
    }
}

private fun KotlinCompilation<*>.hasDefaultSourceSet(): Boolean =
    try {
        defaultSourceSet
        true
    } catch (_: RuntimeException) {
        false
    }

private fun KotlinCompilation<*>.isTest(): Boolean =
    name in TEST_COMPILATION_NAMES ||
        name in lokki.testCompilations.get() ||
        name in target.project.testSuiteSourceSetNames() ||
        platformType == KotlinPlatformType.androidJvm && ANDROID_TEST_SUFFIXES.any(name::endsWith)

private fun KotlinCompilation<*>.isMain(): Boolean = name == KotlinCompilation.MAIN_COMPILATION_NAME || isAndroidVariant()

private fun KotlinCompilation<*>.isAndroidVariant(): Boolean =
    platformType == KotlinPlatformType.androidJvm && name != KotlinCompilation.MAIN_COMPILATION_NAME && !isTest()

private fun Project.testSuiteSourceSetNames(): Set<String> =
    extensions.findByType(TestingExtension::class.java)
        ?.suites
        ?.withType(JvmTestSuite::class.java)
        ?.mapTo(HashSet()) { it.sources.name }
        .orEmpty()

private val PLATFORM_TYPES = setOf(KotlinPlatformType.jvm, KotlinPlatformType.androidJvm)
private val PLUGIN_ORDER = listOf(
    "${BuildConfig.COMPILER_PLUGIN_ID}>androidx.compose.compiler.plugins.kotlin",
    "org.jetbrains.kotlin.parcelize>${BuildConfig.COMPILER_PLUGIN_ID}",
)
private val TEST_COMPILATION_NAMES = setOf(KotlinCompilation.TEST_COMPILATION_NAME, "testFixtures", "hostTest", "deviceTest")
private val ANDROID_TEST_SUFFIXES = listOf("UnitTest", "AndroidTest", "TestFixtures")

private const val SUBPACKAGES = ".**"
private const val RECORD_STACK_TRACES = "lokki.recordStackTraces"
private const val DEBUG = "lokki.debug"
private const val MOCKABLE_PUBLICATION_NOWARN = "lokki.mockablePublication.nowarn"
private const val KOTLIN_COMPATIBILITY_NOWARN = "lokki.kotlinCompatibility.nowarn"
