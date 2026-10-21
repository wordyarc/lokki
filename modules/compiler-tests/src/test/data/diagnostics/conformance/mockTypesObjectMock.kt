// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
package io.lokki.it

import io.lokki.mockObject

fun objectMock() {
    val test = Any()
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>test<!>) {
    }
}
