package io.lokki.compiler.fir

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.linked
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

internal class LokkiFirExtensionRegistrar(
    private val compat: CompilerCompat,
    private val mockable: MockablePatterns?,
    private val restricted: MockablePatterns?,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        linked {
            +FirExtensionSessionComponent.Factory { session ->
                LokkiEnvironment(session, compat.registersClassesAsMetadataVisible, mockable, restricted)
            }
            +::LokkiFirCheckersExtension
            registerDiagnosticContainers(LokkiDiagnostics)
        }
    }
}
