// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import io.lokki.mockStatic
import io.lokki.unmockStatic

fun check(name: String, names: Array<String>) {
    mockStatic(<!LOKKI_FILE_CLASS_NOT_LITERAL!>name<!>)
    mockStatic(<!LOKKI_FILE_CLASS_NOT_LITERAL!>*names<!>)
    unmockStatic(<!LOKKI_FILE_CLASS_NOT_LITERAL!>name + "Kt"<!>)
}
