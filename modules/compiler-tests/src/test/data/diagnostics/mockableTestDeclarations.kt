// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// MOCKABLE: fixture.Named

// FILE: fixture.kt
package fixture

object Named {
    fun time(): Long = 1
}

object Unnamed {
    fun time(): Long = 1
}

class Plain

fun free(): Int = 1

// FILE: test.kt
package test

import fixture.Named
import fixture.Plain
import fixture.Unnamed
import fixture.free
import io.lokki.every
import io.lokki.mock
import io.lokki.mockObject
import io.lokki.mockStatic

fun check() {
    mockObject(Named)
    every { Named.time() } returns 1
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Unnamed<!>)
    every { Unnamed.<!LOKKI_NOT_INTERCEPTABLE!>time<!>() } returns 1
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Plain<!>>()
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::free<!>)
}
