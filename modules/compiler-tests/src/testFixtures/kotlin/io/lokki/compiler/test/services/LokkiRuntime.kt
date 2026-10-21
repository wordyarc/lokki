package io.lokki.compiler.test.services

import io.lokki.compiler.LokkiConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.AnalysisFlag
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.WarningLevel
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import java.io.File

internal class LokkiEnvironmentConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(LokkiDirectives)

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        configuration.addJvmClasspathRoots(runtimeClasspath(module))
        configuration.put(LokkiConfigurationKeys.MOCKABLE, module.directives[LokkiDirectives.MOCKABLE])
    }

    override fun provideAdditionalAnalysisFlags(
        directives: RegisteredDirectives,
        languageVersion: LanguageVersion,
    ): Map<AnalysisFlag<*>, Any?> {
        val levels = directives[LokkiDirectives.WARNING_LEVEL]
        if (levels.isEmpty()) return emptyMap()
        return mapOf(
            AnalysisFlags.warningLevels to levels.associate { level ->
                level.substringBefore(':') to WarningLevel.fromString(level.substringAfter(':'))!!
            }
        )
    }
}

internal class LokkiRuntimeClasspathProvider(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> = runtimeClasspath(module)
}

private fun runtimeClasspath(module: TestModule): List<File> =
    if (LokkiDirectives.WITH_KOTLINX_COROUTINES in module.directives) {
        lokkiRuntimeClasspath + kotlinxCoroutinesClasspath
    } else {
        lokkiRuntimeClasspath
    }

private val lokkiRuntimeClasspath: List<File> = classpath("lokki.runtime.classpath")

private val kotlinxCoroutinesClasspath: List<File> by lazy { classpath("kotlinx.coroutines.classpath") }

private fun classpath(property: String): List<File> =
    System.getProperty(property)?.split(File.pathSeparator)?.map(::File)
        ?: error("System property '$property' is not set")
