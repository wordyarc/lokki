package io.lokki.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

object LokkiConfigurationKeys {
    val MOCKABLE: CompilerConfigurationKey<List<String>> = CompilerConfigurationKey.create("lokki mockable")
    val INTERCEPTABLE: CompilerConfigurationKey<List<String>> = CompilerConfigurationKey.create("lokki interceptable")
    val RESTRICTED: CompilerConfigurationKey<List<String>> = CompilerConfigurationKey.create("lokki restricted")
    val DEBUG: CompilerConfigurationKey<Boolean> = CompilerConfigurationKey.create("lokki debug")
}

class LokkiCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String
        get() = BuildConfig.COMPILER_PLUGIN_ID
    override val pluginOptions: Collection<CliOption>
        get() = listOf(MOCKABLE_OPTION, INTERCEPTABLE_OPTION, RESTRICTED_OPTION, DEBUG_OPTION)

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            MOCKABLE_OPTION -> {
                mockablePatternProblem(value)?.let { throw CliOptionProcessingException(it) }
                configuration.appendList(LokkiConfigurationKeys.MOCKABLE, value)
            }
            INTERCEPTABLE_OPTION -> {
                mockablePatternProblem(value, INTERCEPTABLE_OPTION.optionName)?.let { throw CliOptionProcessingException(it) }
                configuration.appendList(LokkiConfigurationKeys.INTERCEPTABLE, value)
            }
            RESTRICTED_OPTION -> {
                mockablePatternProblem(value, RESTRICTED_OPTION.optionName)?.let { throw CliOptionProcessingException(it) }
                configuration.appendList(LokkiConfigurationKeys.RESTRICTED, value)
            }
            DEBUG_OPTION -> configuration.put(LokkiConfigurationKeys.DEBUG, value.toBooleanStrict())
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

private val INTERCEPTABLE_OPTION = CliOption(
    BuildConfig.INTERCEPTABLE_OPTION,
    "<pattern>",
    "A package followed by '.**', a class, an object, an annotation or a function whose declarations get " +
        "interception points: classes are also opened, and their constructor calls are intercepted",
    required = false,
    allowMultipleOccurrences = true,
)

private val RESTRICTED_OPTION = CliOption(
    BuildConfig.RESTRICTED_OPTION,
    "<pattern>",
    "A package followed by '.**', a class or an annotation whose classes should not be mocked",
    required = false,
    allowMultipleOccurrences = true,
)

private val DEBUG_OPTION = CliOption(
    BuildConfig.DEBUG_OPTION,
    "<true|false>",
    "Report the code of every file that lokki changes as a Kotlin-like dump of its IR",
    required = false,
)
