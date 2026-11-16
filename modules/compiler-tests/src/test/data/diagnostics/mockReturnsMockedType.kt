// RUN_PIPELINE_TILL: FRONTEND

import io.lokki.mock

interface Car

fun take(text: String) = Unit

fun test() {
    val car: Car = mock<Car>()
    val inferred: Car = mock()
    take(<!ARGUMENT_TYPE_MISMATCH!>mock<Car>()<!>)
}
