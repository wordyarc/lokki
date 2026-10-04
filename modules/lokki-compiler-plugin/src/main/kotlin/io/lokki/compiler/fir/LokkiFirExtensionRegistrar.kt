package io.lokki.compiler.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class LokkiFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::LokkiFirCheckersExtension
    }
}
