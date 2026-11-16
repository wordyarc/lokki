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
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

public class LokkiGradleSubplugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        val lokki = target.extensions.create("lokki", LokkiGradleExtension::class.java)
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
        kotlinCompilation.compileTaskProvider.configure {
            it.compilerOptions.freeCompilerArgs.add(
                "-Xcompiler-plugin-order=${BuildConfig.COMPILER_PLUGIN_ID}>androidx.compose.compiler.plugins.kotlin"
            )
        }
        val test = kotlinCompilation.isTest()
        if (test || lokki.applyToMain.get()) {
            kotlinCompilation.defaultSourceSet.dependencies {
                implementation("${BuildConfig.GROUP}:lokki-core:${BuildConfig.VERSION}")
            }
        }
        if (!test && lokki.mockable.get().isNotEmpty()) kotlinCompilation.warnAboutMockableBytecode()
        return lokki.debug.zip(lokki.mockable) { debug, patterns ->
            val mockable = if (test) emptyList() else patterns.map { SubpluginOption(BuildConfig.MOCKABLE_OPTION, it) }
            mockable + listOfNotNull(SubpluginOption(BuildConfig.DEBUG_OPTION, "true").takeIf { debug })
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
    val used = kotlinVersionOrNull(version) ?: return
    val oldest = KotlinToolingVersion(BuildConfig.MIN_KOTLIN_VERSION)
    val latest = KotlinToolingVersion(BuildConfig.LATEST_TESTED_KOTLIN)
    if (used < oldest) {
        throw GradleException(
            "lokki ${BuildConfig.VERSION} requires Kotlin ${BuildConfig.MIN_KOTLIN_VERSION} or newer, but the build " +
                "uses Kotlin $version: the lokki compiler plugin has no adapter to its compiler API."
        )
    }
    if (used > latest && providers.gradleProperty(KOTLIN_COMPATIBILITY_NOWARN).orNull?.toBoolean() != true) {
        logger.warn(
            "lokki ${BuildConfig.VERSION} is tested with Kotlin up to ${BuildConfig.LATEST_TESTED_KOTLIN}, but the " +
                "build uses Kotlin $version: if the lokki compiler plugin fails, use Kotlin " +
                "${BuildConfig.LATEST_TESTED_KOTLIN} or a newer lokki. To disable this warning, set " +
                "$KOTLIN_COMPATIBILITY_NOWARN=true in gradle.properties."
        )
    }
}

private fun kotlinVersionOrNull(version: String): KotlinToolingVersion? {
    val parsed = runCatching { KotlinToolingVersion(version) }.getOrNull() ?: return null
    return if (parsed.isDev) KotlinToolingVersion(parsed.toKotlinVersion()) else parsed
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
private val TEST_COMPILATION_NAMES = setOf(KotlinCompilation.TEST_COMPILATION_NAME, "testFixtures", "hostTest", "deviceTest")
private val ANDROID_TEST_SUFFIXES = listOf("UnitTest", "AndroidTest", "TestFixtures")

private const val RECORD_STACK_TRACES = "lokki.recordStackTraces"
private const val MOCKABLE_PUBLICATION_NOWARN = "lokki.mockablePublication.nowarn"
private const val KOTLIN_COMPATIBILITY_NOWARN = "lokki.kotlinCompatibility.nowarn"
