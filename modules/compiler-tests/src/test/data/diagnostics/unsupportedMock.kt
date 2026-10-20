// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: lib/Base.java
package lib;

public abstract class Base {
    abstract void hidden();
}

// FILE: middle/Middle.java
package middle;

public abstract class Middle extends lib.Base {
}

// FILE: test.kt
@file:Suppress("LOKKI_TYPE_ARGUMENT_NOT_CONCRETE", "LOKKI_TYPE_NOT_MOCKABLE")

package sample

import io.lokki.mock
import kotlin.reflect.KClass
import lib.Base
import middle.Middle

class Bolt

object Garage

abstract class Singleton private constructor()

sealed interface Shape

interface Node<T : Node<T>>

interface Car

interface IntId {
    fun id(): Int
}

interface StringId {
    fun id(): String
}

interface ValInt {
    val size: Int
}

interface VarNumber {
    var size: Number
}

interface Repo<T>

interface StringRepo : Repo<String>

interface IntRepo : Repo<Int>

inline fun <reified T : Any> create(): T = <!LOKKI_UNSUPPORTED!>mock<T>()<!>

inline fun <reified T : Any> withInterface(): Car = <!LOKKI_UNSUPPORTED!>mock<Car>(moreInterfaces = arrayOf(T::class))<!>

fun test() {
    <!LOKKI_UNSUPPORTED!>mock<Bolt>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Garage>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Singleton>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Base>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Shape>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Middle>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Node<*>>()<!>
}

fun testMoreInterfaces(type: KClass<*>) {
    <!LOKKI_UNSUPPORTED!>mock<Car>(moreInterfaces = arrayOf(type))<!>
    <!LOKKI_UNSUPPORTED!>mock<Car>(moreInterfaces = arrayOf(Bolt::class))<!>
    <!LOKKI_UNSUPPORTED!>mock<Car>(moreInterfaces = arrayOf(Shape::class))<!>
    <!LOKKI_UNSUPPORTED!>mock<IntId>(moreInterfaces = arrayOf(StringId::class))<!>
    <!LOKKI_UNSUPPORTED!>mock<ValInt>(moreInterfaces = arrayOf(VarNumber::class))<!>
    <!LOKKI_UNSUPPORTED!>mock<StringRepo>(moreInterfaces = arrayOf(IntRepo::class))<!>
}
