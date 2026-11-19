package io.lokki.compiler.test.services

import io.lokki.compiler.LokkiCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.service

fun TestConfigurationBuilder.configurePlugin() {
    useConfigurators(::LokkiExtensionRegistrarConfigurator, ::LokkiEnvironmentConfigurator)
    useCustomRuntimeClasspathProviders(::LokkiRuntimeClasspathProvider)
}

private class LokkiExtensionRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    private val registrar = LokkiCompilerPluginRegistrar()

    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::RecordedLookups))

    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        val tracked = configuration.copy()
        tracked.put(CommonConfigurationKeys.LOOKUP_TRACKER, testServices.recordedLookups.trackerOf(module))
        with(registrar) { registerExtensions(tracked) }
    }
}
