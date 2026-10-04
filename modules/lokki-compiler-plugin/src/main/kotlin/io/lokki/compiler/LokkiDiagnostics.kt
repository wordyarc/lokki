package io.lokki.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

internal object LokkiDiagnostics : KtDiagnosticsContainer() {
    val LOKKI_UNSUPPORTED by error1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = LokkiDiagnosticRenderers
}

private object LokkiDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Lokki") { map ->
        map.put(LokkiDiagnostics.LOKKI_UNSUPPORTED, "{0}.", CommonRenderers.STRING)
    }
}
