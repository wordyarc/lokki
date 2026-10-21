package io.lokki.compiler.fir

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.Mockable
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension

class LokkiFirExtensionRegistrar(private val mockable: Mockable?) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::LokkiFirCheckersExtension
        registerDiagnosticContainers(LokkiDiagnostics)
        if (mockable != null) {
            +FirStatusTransformerExtension.Factory { session -> MockableStatusTransformer(session, mockable) }
        }
    }
}
