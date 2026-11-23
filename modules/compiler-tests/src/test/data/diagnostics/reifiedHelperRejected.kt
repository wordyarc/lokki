// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
import io.lokki.mock

class Final

inline fun <reified T : Any> helper(): T = mock<T>()

inline fun <reified T : Any> throughHelper(): T = helper<T>()

fun test() {
    <!LOKKI_TYPE_NOT_MOCKABLE!>helper<Final>()<!>
    <!LOKKI_TYPE_NOT_MOCKABLE!>throughHelper<String>()<!>
}
