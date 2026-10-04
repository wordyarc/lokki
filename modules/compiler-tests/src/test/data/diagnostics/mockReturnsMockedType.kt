// RUN_PIPELINE_TILL: FRONTEND

import io.lokki.mock

interface Car

fun test() {
    val car: Car = mock<Car>()
    val inferred: Car = mock()
    val text: String <!INITIALIZER_TYPE_MISMATCH!>=<!> mock<Car>()
}
