package io.lokki.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

class LokkiCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String
        get() = BuildConfig.COMPILER_PLUGIN_ID
    override val pluginOptions: Collection<CliOption>
        get() = listOf(MOCKABLE_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            MOCKABLE_OPTION -> {
                mockablePatternProblem(value)?.let { throw CliOptionProcessingException(it) }
                configuration.appendList(LokkiConfigurationKeys.MOCKABLE, value)
            }
            else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
        }
    }
}

private val MOCKABLE_OPTION = CliOption(
    BuildConfig.MOCKABLE_OPTION,
    "<pattern>",
    "A package followed by '.**', a class or an annotation whose classes and their members become open",
    required = false,
    allowMultipleOccurrences = true,
)
