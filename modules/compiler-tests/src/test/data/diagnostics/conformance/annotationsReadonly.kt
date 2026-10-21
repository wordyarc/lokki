// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.Mock

open class MockCls {
    open fun op(a: Int): Int = a + 1
}

class ReadonlyAnnotatedCls {
    <!LOKKI_MOCK_PROPERTY_NOT_MUTABLE!>@Mock<!>
    val mock: MockCls? = null
}
