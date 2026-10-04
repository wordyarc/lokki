package io.lokki.compiler.fir

import io.lokki.compiler.LokkiDiagnostics
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class LokkiFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::LokkiFirCheckersExtension
        registerDiagnosticContainers(LokkiDiagnostics)
    }
}
