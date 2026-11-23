// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
import io.lokki.every

open class Service {
    open var mode: String = ""

    open val fixed: String = ""

    open fun run(a: Int) = a

    private val hiddenField = 1
}

fun test(service: Service, name: String, arguments: List<Any?>) {
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>service getProperty name<!> } returns "x"
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>service invoke "run" withArguments arguments<!> } returns 1
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>service invoke name withArguments listOf(1)<!> } returns 1
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>service invokeNoArgs "absent"<!> } returns 1
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>service getProperty "absent"<!> } returns 1
    every { <!LOKKI_NOT_INTERCEPTABLE!>service getProperty "hiddenField"<!> } returns 1
    every { <!LOKKI_UNRESOLVED_DYNAMIC_CALL!>service setProperty "fixed" value "x"<!> } returns Unit
}
