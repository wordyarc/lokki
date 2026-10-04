package io.lokki.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtElement

internal object LokkiDiagnostics : KtDiagnosticsContainer() {
    val LOKKI_UNSUPPORTED by error1<KtElement, String>()
    val LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION by error0<KtElement>()
    val LOKKI_STUB_BLOCK_NOT_LITERAL by error1<KtElement, String>()
    val LOKKI_STUB_BLOCK_NOT_SINGLE_CALL by error2<KtElement, String, String>()
    val LOKKI_NOT_INTERCEPTABLE by error2<KtElement, String, String>()
    val LOKKI_TYPE_NOT_MOCKABLE by error2<KtElement, String, String>()
    val LOKKI_TYPE_ARGUMENT_NOT_CONCRETE by error2<KtElement, String, String>()
    val LOKKI_CHAINED_CALL_DISABLED by error0<KtElement>()
    val LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC by error1<KtElement, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = LokkiDiagnosticRenderers
}

private object LokkiDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Lokki") { map ->
        map.put(LokkiDiagnostics.LOKKI_UNSUPPORTED, "{0}.", CommonRenderers.STRING)
        map.put(
            LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION,
            "A matcher must be a whole argument of the stubbed call or an operand of ''and'', ''or'', ''not''.",
        )
        map.put(
            LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_LITERAL,
            "The block of ''{0}'' must be a lambda literal.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_SINGLE_CALL,
            "The block of ''{0}'' must be a single call of a mock member, but it {1}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_NOT_INTERCEPTABLE,
            "A call of ''{0}'' cannot be intercepted: {1}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE,
            "''{0}'' cannot be mocked: {1}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE,
            "The type argument of ''{0}'' must be known at compile time, but ''{1}'' is a type parameter.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_CHAINED_CALL_DISABLED,
            "A matcher in the receiver of the stubbed call needs chained mocks, which are disabled: " +
                "stub the call in the receiver separately.",
        )
        map.put(
            LokkiDiagnostics.LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC,
            "''{0}'' must be called directly: a reference to it is not supported.",
            CommonRenderers.STRING,
        )
    }
}
