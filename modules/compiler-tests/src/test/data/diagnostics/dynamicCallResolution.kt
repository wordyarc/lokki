// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.mock

abstract class Template {
    private fun secret(): String = "secret"
    protected fun closed(): String = "closed"
    protected <!NOTHING_TO_INLINE!>inline<!> fun inlined(): String = "inlined"
    protected abstract fun hook(value: Int): String
    protected abstract fun overloaded(value: Int): String
    protected abstract fun overloaded(value: String): String
    protected open fun finalize() {}
}

fun test(name: String, values: Array<Any?>) {
    val template = mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Template<!>>()
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>template["missing"]()<!> } returns "a"
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>template[name]()<!> } returns "a"
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>template["hook"]("text")<!> } returns "a"
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>template["hook"](*values)<!> } returns "a"
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>template["overloaded"](any())<!> } returns "a"
    every { <!LOKKI_NOT_INTERCEPTABLE!>template["secret"]()<!> } returns "a"
    every { <!LOKKI_NOT_INTERCEPTABLE!>template["closed"]()<!> } returns "a"
    every { <!LOKKI_NOT_INTERCEPTABLE!>template["inlined"]()<!> } returns "a"
    every { <!LOKKI_NOT_INTERCEPTABLE!>template["finalize"]()<!> } returns "a"
}
