// FILE: helpers.kt
package test

import io.lokki.mock

interface Engine {
    fun start(): String
}

interface Car {
    fun engine(): Engine
}

interface Box<T> {
    fun get(): T
}

inline fun <reified T : Any> relaxed(): T = mock<T>(relaxed = true)

inline fun <reified T : Any> later(): () -> T = { mock<T>(relaxed = true) }

inline fun <reified T : Any> boxed(): Box<T> = relaxed<Box<T>>()

// FILE: plain.kt
package test

fun plainCar(): Car = relaxed<Car>()

fun boxedInt(): Box<Int> = boxed<Int>()

fun boxOfInt(): Box<Int> = relaxed<Box<Int>>()

// FILE: chained.kt
@file:ChainedMocks

package test

import io.lokki.ChainedMocks

fun chainedCar(): Car = relaxed<Car>()

fun laterChainedCar(): () -> Car = later<Car>()

open class Garage(val car: Car)

class ChainedGarage : Garage(relaxed<Car>())

// FILE: test.kt
package test

import io.lokki.LokkiException

private fun startOf(car: Car): String =
    try {
        "start=[" + car.engine().start() + "]"
    } catch (_: LokkiException) {
        "no default"
    }

private fun valueOf(box: Box<Int>): String =
    try {
        "value=" + box.get()
    } catch (_: LokkiException) {
        "no default"
    }

fun box(): String {
    if (startOf(plainCar()) != "no default") return "Fail plain call site first"
    if (startOf(chainedCar()) != "start=[]") return "Fail chained call site after a plain one"
    if (startOf(plainCar()) != "no default") return "Fail plain call site after a chained one"
    val later = laterChainedCar()
    if (startOf(later()) != "start=[]") return "Fail mock created after the call of the helper"
    if (startOf(ChainedGarage().car) != "start=[]") return "Fail helper in an argument of a super constructor"
    if (valueOf(boxedInt()) != "no default") return "Fail type parameter inside the mocked type"
    if (valueOf(boxOfInt()) != "value=0") return "Fail concrete type after a helper with its type parameter"
    if (valueOf(boxedInt()) != "no default") return "Fail type parameter inside the mocked type after a concrete one"
    return "OK"
}
