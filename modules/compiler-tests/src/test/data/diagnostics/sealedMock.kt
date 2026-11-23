// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock
import io.lokki.spy

sealed interface OnlyFinal

class FinalShape : OnlyFinal

sealed interface Empty

sealed interface Mockable

open class OpenShape : Mockable

sealed class Nested {
    sealed class Inner : Nested()

    object Single : Inner()
}

interface Car

fun test() {
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>OnlyFinal<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Empty<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Nested<!>>()
    mock<Mockable>()
    spy<<!LOKKI_TYPE_NOT_MOCKABLE!>Mockable<!>>()
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_TYPE_NOT_MOCKABLE!>Mockable::class<!>))
}
