// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:Suppress("LOKKI_TYPE_ARGUMENT_NOT_CONCRETE", "LOKKI_TYPE_NOT_MOCKABLE")

package sample

import io.lokki.mock
import kotlin.reflect.KClass

class Bolt

object Garage

sealed interface Shape

interface Car

inline fun <reified T : Any> create(): T = mock<T>()

inline fun <reified T : Any> withInterface(): Car = mock<Car>(moreInterfaces = arrayOf(<!LOKKI_UNSUPPORTED!>T::class<!>))

fun test() {
    <!LOKKI_UNSUPPORTED!>mock<Bolt>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Garage>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Shape>()<!>
}

fun testMoreInterfaces(type: KClass<*>) {
    <!LOKKI_UNSUPPORTED!>mock<Car>(moreInterfaces = arrayOf(type))<!>
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_UNSUPPORTED!>Bolt::class<!>))
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_UNSUPPORTED!>Shape::class<!>))
}
