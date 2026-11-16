// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock

interface Car

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

fun test() {
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<IntId>(moreInterfaces = arrayOf(StringId::class))<!>
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<Car>(moreInterfaces = arrayOf(IntId::class, StringId::class))<!>
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<ValInt>(moreInterfaces = arrayOf(VarNumber::class))<!>
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<StringRepo>(moreInterfaces = arrayOf(IntRepo::class))<!>

    mock<IntId>(moreInterfaces = arrayOf(NumberId::class))
    mock<ValInt>(moreInterfaces = arrayOf(ValNumber::class))
    mock<VarInt>(moreInterfaces = arrayOf(ValNumber::class))
    mock<Generic>(moreInterfaces = arrayOf(OtherGeneric::class))
    mock<StringRepo>(moreInterfaces = arrayOf(Repo::class))
    mock<Repo<String>>(moreInterfaces = arrayOf(StringRepo::class, Car::class))
}
