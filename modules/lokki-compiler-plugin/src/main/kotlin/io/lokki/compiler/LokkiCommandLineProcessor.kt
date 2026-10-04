package io.lokki.compiler

import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor

class LokkiCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String
        get() = BuildConfig.COMPILER_PLUGIN_ID
    override val pluginOptions: Collection<CliOption>
        get() = emptyList()
}
