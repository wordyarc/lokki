// WITH_KOTLINX_COROUTINES
// FILE: helpers.kt
package test

import io.lokki.every
import io.lokki.mock
import io.lokki.mockClass

interface Car {
    fun speed(): Int
}

interface Box<T> {
    fun get(): T
}

inline fun <reified T : Any> relaxedMock(): T = mock<T>(relaxed = true)

inline fun <reified T : Any> throughHelper(): T = relaxedMock<T>()

inline fun <reified A : Any, reified B : Any> both(): Pair<A, B> = mock<A>(relaxed = true) to mock<B>(relaxed = true)

inline fun <reified T : Any> named(name: String): T = mock<T>(name = name, relaxed = true)

inline fun <reified T : Any> configured(block: T.() -> Unit): T = mock<T>(block = block)

inline fun <reified T : Any> boxed(): Box<T> = relaxedMock<Box<T>>()

inline fun <reified T : Any> byClass(): T = mockClass(T::class, relaxed = true)

// FILE: test.kt
package test

import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.verify

private inline fun <reified T : Any> local(): T = relaxedMock<T>()

fun box(): String = lokkiScope {
    val car = relaxedMock<Car>()
    if (car.speed() != 0) return@lokkiScope "relaxed"
    every { car.speed() } returns 7
    if (car.speed() != 7) return@lokkiScope "stubbed"
    verify(exactly = 2) { car.speed() }
    if (throughHelper<Car>().speed() != 0) return@lokkiScope "transitive"
    if (local<Car>().speed() != 0) return@lokkiScope "private helper"
    val (first, second) = both<Car, Box<String>>()
    if (first.speed() != 0 || second.get() != "") return@lokkiScope "two parameters"
    if (!named<Car>("wheels").toString().contains("wheels")) return@lokkiScope "name"
    val stubbed = configured<Car> { every { speed() } returns 3 }
    if (stubbed.speed() != 3) return@lokkiScope "block"
    val strings = relaxedMock<Box<String>>()
    val ints = relaxedMock<Box<Int>>()
    if (strings.get() != "" || ints.get() != 0) return@lokkiScope "generic arguments"
    val nested: Box<Int> = boxed<Int>()
    if (nested.get() != 0) return@lokkiScope "type parameter inside the argument"
    if (byClass<Car>().speed() != 0) return@lokkiScope "mockClass"
    "OK"
}
