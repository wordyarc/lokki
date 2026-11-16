// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock
import kotlin.reflect.KClass

interface Car

open class Engine

annotation class Marker

sealed interface Shape

interface Repo<T>

interface StringRepo : Repo<String>

interface IntRepo : Repo<Int>

interface IntId {
    fun id(): Int
}

interface StringId {
    fun id(): String
}

interface NumberId {
    fun id(): Number
}

interface ValNumber {
    val size: Number
}

interface ValInt {
    val size: Int
}

interface VarNumber {
    var size: Number
}

interface VarInt {
    var size: Int
}

interface Generic {
    fun <R> convert(value: R): R
}

interface OtherGeneric {
    fun <R> convert(value: R): R
}

inline fun <reified T : Any> withInterface(): Car = mock<Car>(moreInterfaces = arrayOf(<!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>T::class<!>))

fun test(type: KClass<*>, types: Array<KClass<*>>) {
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>type<!>))
    mock<Car>(moreInterfaces = <!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>types<!>)
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_TYPE_NOT_MOCKABLE!>Engine::class<!>))
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_TYPE_NOT_MOCKABLE!>Marker::class<!>))
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_TYPE_NOT_MOCKABLE!>Shape::class<!>))
    mock<Car>("car", false, <!LOKKI_TYPE_NOT_MOCKABLE!>Engine::class<!>)

    mock<IntId>(moreInterfaces = arrayOf(NumberId::class))
    mock<ValInt>(moreInterfaces = arrayOf(ValNumber::class))
    mock<VarInt>(moreInterfaces = arrayOf(ValNumber::class))
    mock<Generic>(moreInterfaces = arrayOf(OtherGeneric::class))
    mock<StringRepo>(moreInterfaces = arrayOf(Repo::class))
    mock<Repo<String>>(moreInterfaces = arrayOf(StringRepo::class, Car::class))
}
