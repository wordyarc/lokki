package io.lokki.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Provider
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class LokkiGradleSubplugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.platformType == KotlinPlatformType.jvm &&
            (kotlinCompilation.name in TEST_COMPILATION_NAMES ||
                kotlinCompilation.name in kotlinCompilation.target.project.testSuiteSourceSetNames())

    override fun getCompilerPluginId(): String = BuildConfig.COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(BuildConfig.GROUP, "lokki-compiler-plugin", BuildConfig.VERSION)

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        kotlinCompilation.defaultSourceSet.dependencies {
            implementation("${BuildConfig.GROUP}:lokki-core:${BuildConfig.VERSION}")
        }
        kotlinCompilation.compileTaskProvider.configure {
            it.compilerOptions.freeCompilerArgs.add(
                "-Xcompiler-plugin-order=${BuildConfig.COMPILER_PLUGIN_ID}>androidx.compose.compiler.plugins.kotlin"
            )
        }
        return kotlinCompilation.target.project.provider { emptyList() }
    }
}

private val TEST_COMPILATION_NAMES = setOf(KotlinCompilation.TEST_COMPILATION_NAME, "testFixtures")

private fun Project.testSuiteSourceSetNames(): Set<String> =
    extensions.findByType(TestingExtension::class.java)
        ?.suites
        ?.withType(JvmTestSuite::class.java)
        ?.mapTo(HashSet()) { it.sources.name }
        .orEmpty()
