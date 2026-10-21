// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
package test

import io.lokki.every
import io.lokki.mockObject

object Local {
    fun f(): Int = 1
}

open class Service {
    fun f(): Int = 1
}

enum class Mode { ECO }

fun test(objects: Array<Any>) {
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Local<!>)
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Service()<!>)
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Mode.ECO<!>)
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>*objects<!>)
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Any()<!>) {}
    every { Local.<!LOKKI_NOT_INTERCEPTABLE!>f<!>() } returns 2
}
