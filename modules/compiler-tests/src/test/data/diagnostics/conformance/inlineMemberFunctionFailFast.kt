// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.mock

open class HasInline {
    <!NOTHING_TO_INLINE!>inline<!> fun addOne(x: Int) = x + 1
}

open class HasWrapper {
    private val impl = HasInline()

    open fun addOne(x: Int) = impl.addOne(x)
}

fun stubOfNonInlineWrapperReturnsConfiguredValue() {
    val w = mock<HasWrapper>()
    every { w.addOne(10) } returns 42
}

fun stubOfInlineFunctionFromKotlinThrowsDescriptiveError() {
    val inlineMock = mock<HasInline>()
    every { inlineMock.<!LOKKI_NOT_INTERCEPTABLE!>addOne<!>(any()) } returns 42
}
