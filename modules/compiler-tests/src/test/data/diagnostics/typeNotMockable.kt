// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock

interface Car

open class Engine

abstract class Wheel

class Bolt

data class Plate(val number: String)

@JvmInline
value class Vin(val value: String)

object Garage

enum class Color { RED }

annotation class Marker

sealed interface Shape

sealed class Part

typealias Screw = Bolt

typealias Vehicle = Car

fun test() {
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Bolt<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Plate<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Vin<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Garage<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Color<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Marker<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Shape<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Part<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Screw<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>String<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Int<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Array<String><!>>()
    mock<<!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>Nothing<!>>()
    val bolt: Bolt = <!LOKKI_TYPE_NOT_MOCKABLE!>mock()<!>
    val named = mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Bolt<!>>("bolt") { }

    mock<Car>()
    mock<Vehicle>()
    mock<Engine>()
    mock<Wheel>()
    mock<Map<String, Int>>()
    mock<() -> Unit>()
    mock<Runnable>()
    val car: Car = mock()

    class Local
    abstract class OpenLocal
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Local<!>>()
    mock<OpenLocal>()
}
