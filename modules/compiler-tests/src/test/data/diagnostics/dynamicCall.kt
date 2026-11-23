// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.Called
import io.lokki.every
import io.lokki.mock
import io.lokki.verify

abstract class Template {
    protected abstract fun hook(): String
    open fun take(value: String): String = value
}

fun test() {
    val template = mock<Template>()
    every { template.take(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>template["hook"]<!>() as String) } returns "a"
    verify { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>template["hook"]<!>()!! wasNot Called }
    every { template["hook"]().<!LOKKI_NOT_INTERCEPTABLE!>toString<!>() } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>template["hook"]<!> } returns <!ARGUMENT_TYPE_MISMATCH!>"a"<!>
}
