package io.lokki.compiler.fir

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.linked
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension

internal class LokkiFirExtensionRegistrar(
    private val mockable: MockablePatterns?,
    private val compat: CompilerCompat,
) :
    FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        linked {
            +FirExtensionSessionComponent.Factory { session ->
                LokkiEnvironment(session, compat.registersClassesAsMetadataVisible)
            }
            +::LokkiFirCheckersExtension
            registerDiagnosticContainers(LokkiDiagnostics)
            if (mockable != null) {
                +FirStatusTransformerExtension.Factory { session -> MockableStatusTransformer(session, mockable) }
            }
        }
    }
}
