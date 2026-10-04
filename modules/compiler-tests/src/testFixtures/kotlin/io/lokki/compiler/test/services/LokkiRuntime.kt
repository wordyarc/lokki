package io.lokki.compiler.test.services

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import java.io.File

internal class LokkiEnvironmentConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        configuration.addJvmClasspathRoots(lokkiRuntimeClasspath)
    }
}

internal class LokkiRuntimeClasspathProvider(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> = lokkiRuntimeClasspath
}

private val lokkiRuntimeClasspath: List<File> =
    System.getProperty("lokki.runtime.classpath")?.split(File.pathSeparator)?.map(::File)
        ?: error("System property 'lokki.runtime.classpath' is not set")
