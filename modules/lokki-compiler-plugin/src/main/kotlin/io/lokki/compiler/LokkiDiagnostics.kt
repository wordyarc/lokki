package io.lokki.compiler

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.error3
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning0
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.diagnostics.warning2
import org.jetbrains.kotlin.psi.KtElement

internal object LokkiDiagnostics : KtDiagnosticsContainer() {
    val LOKKI_UNSUPPORTED by error1<KtElement, String>()
    val LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION by error0<KtElement>()
    val LOKKI_STUB_BLOCK_NOT_LITERAL by error1<KtElement, String>()
    val LOKKI_STUB_BLOCK_NOT_SINGLE_CALL by error3<KtElement, String, String, String>()
    val LOKKI_NOT_INTERCEPTABLE by error2<KtElement, String, String>()
    val LOKKI_TYPE_NOT_MOCKABLE by error2<KtElement, String, String>()
    val LOKKI_TYPE_ARGUMENT_NOT_CONCRETE by error3<KtElement, String, String, String>()
    val LOKKI_CHAINED_CALL_DISABLED by error0<KtElement>()
    val LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC by error1<KtElement, String>()
    val LOKKI_STUB_WITHOUT_ANSWER by error1<KtElement, String>()
    val LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE by error3<KtElement, Int, String, Int>()
    val LOKKI_ALL_ANY_NOT_FIRST by warning0<KtElement>()
    val LOKKI_RESTRICTED_TYPE by warning2<KtElement, String, String>()
    val LOKKI_NOT_RECORDED by error1<KtElement, String>()
    val LOKKI_USELESS_EXCLUSION by warning1<KtElement, String>()
    val LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT by warning1<KtElement, String>()
    val LOKKI_WITH_ARG_PREDICATE_NOT_USED by warning1<KtElement, String>()
    val LOKKI_SLOT_READ_BEFORE_CAPTURE by error2<KtElement, String, String>()
    val LOKKI_CONFLICTING_VERIFY_ARGUMENTS by error2<KtElement, String, String>()
    val LOKKI_FINAL_MEMBERS_NOT_MOCKED by warning2<KtElement, String, String>()
    val LOKKI_ABSTRACT_ORIGINAL_CALL by error2<KtElement, String, String>()
    val LOKKI_MISSING_COROUTINES_DEPENDENCY by error1<KtElement, String>()
    val LOKKI_REF_EQ_OF_VALUE_CLASS by error2<KtElement, String, String>()
    val LOKKI_UNSAFE_SPY_DELEGATION by error3<KtElement, String, String, String>()
    val LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL by warning2<KtElement, String, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = LokkiDiagnosticRenderers
}

private object LokkiDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("Lokki") { map ->
        map.put(LokkiDiagnostics.LOKKI_UNSUPPORTED, "{0}.", CommonRenderers.STRING)
        map.put(
            LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION,
            "A matcher must be a whole argument of the stubbed or verified call " +
                "or an operand of 'and', 'or', 'not'.",
        )
        map.put(
            LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_LITERAL,
            "The block of ''{0}'' must be a lambda literal.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_SINGLE_CALL,
            "The block of ''{0}'' must {1}, but it {2}.",
            CommonRenderers.STRING,
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
            "{0} of ''{1}'' must be known at compile time, but {2}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_CHAINED_CALL_DISABLED,
            "A matcher in the receiver of the stubbed or verified call needs chained mocks: " +
                "stub or verify the call in the receiver separately, or annotate the file with @file:ChainedMocks.",
        )
        map.put(
            LokkiDiagnostics.LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC,
            "''{0}'' must be called directly: a reference to it is not supported.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_STUB_WITHOUT_ANSWER,
            "''{0}'' registers nothing without an answer: " +
                "add ''returns'', ''answers'', ''throws'' or ''just Runs'' after the block.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE,
            "''returnsArgument({0})'' is out of range: the number of arguments of ''{1}'' is {2}.",
            KtDiagnosticRenderers.TO_STRING,
            CommonRenderers.STRING,
            KtDiagnosticRenderers.TO_STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_ALL_ANY_NOT_FIRST,
            "'allAny' replaces the other arguments only as the first argument of the stubbed or verified call: " +
                "here it matches like 'any'.",
        )
        map.put(
            LokkiDiagnostics.LOKKI_RESTRICTED_TYPE,
            "''{0}'' should not be mocked: {1}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_NOT_RECORDED,
            "''{0}'' is not recorded: calls of equals, hashCode and toString cannot be verified.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_USELESS_EXCLUSION,
            "''{0}'' is not recorded, so there is nothing to exclude: calls of equals, hashCode and toString " +
                "are never recorded.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT,
            "''wasNot Called'' is applied to the result of ''{0}'', not to a mock: " +
                "to check that ''{0}'' was not called, verify the call with ''exactly = 0''.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_WITH_ARG_PREDICATE_NOT_USED,
            "''{0}'' ignores the result of its lambda, so this predicate does not filter calls: " +
                "use ''match'' instead or make it an assertion.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_SLOT_READ_BEFORE_CAPTURE,
            "The slot is read before ''capture'' fills it: the block of ''{0}'' is evaluated before calls are " +
                "matched against it. {1}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_CONFLICTING_VERIFY_ARGUMENTS,
            "Invalid call counts in ''{0}'': {1}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_FINAL_MEMBERS_NOT_MOCKED,
            "The mock of ''{0}'' does not intercept its final members {1}: they run the original code on an " +
                "instance created without a constructor. Make them open.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_ABSTRACT_ORIGINAL_CALL,
            "''{0}'' is abstract, so ''{1}()'' has no implementation to call.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_MISSING_COROUTINES_DEPENDENCY,
            "''{0}'' waits in ''kotlinx.coroutines.awaitCancellation()'', which is not on the classpath of this " +
                "compilation: add ''org.jetbrains.kotlinx:kotlinx-coroutines-core'' to the test dependencies.",
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_REF_EQ_OF_VALUE_CLASS,
            "''{0}'' compares references, but value class ''{1}'' has no identity: a mock receives the argument " +
                "in a new box on every call. Use ''eq'' or compare the underlying value in ''match''.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_UNSAFE_SPY_DELEGATION,
            "A spy of ''{0}'' cannot delegate to an existing instance: it cannot forward {1} to the instance, " +
                "so they would run on an object created without a constructor. " +
                "Pass a constructor call instead, such as ''spy({2}(...))''.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        map.put(
            LokkiDiagnostics.LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL,
            "The spy of ''{0}'' delegates to an existing instance: calls that the instance makes on itself " +
                "bypass the spy, and the spy is a different object. Pass a constructor call, such as " +
                "''spy({1}(...))'', to spy on the object itself.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
    }
}
