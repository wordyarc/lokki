// MODULE: main
// INTERCEPTABLE: sample.Service sample.WithDefault sample.Typed
// FILE: main.kt
package sample

open class Base {
    open fun greet(name: String): String = "hello $name"

    open var mode: String = "eco"

    open suspend fun load(): Int = 1

    fun sealedByFinal(): String = "final"
}

interface Defaults {
    fun fallback(): String = "default"
}

object Service : Base() {
    fun own(): String = "own"

    fun viaInherited(): String = greet("own")
}

object WithDefault : Defaults

open class Generic<T> {
    open fun echo(value: T): T = value
}

object Typed : Generic<Int>()

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mockObject
import io.lokki.verify
import sample.Base
import sample.Service
import sample.Typed
import sample.WithDefault

fun box(): String =
    lokkiScope {
        mockObject(Service)
        if (Service.greet("a") != "hello a") return@lokkiScope "Fail unstubbed inherited member"
        every { Service.greet("a") } returns "mocked"
        if (Service.greet("a") != "mocked") return@lokkiScope "Fail stub of an inherited member"
        if ((Service as Base).greet("a") != "mocked") return@lokkiScope "Fail call through the base class"
        every { Service.greet("own") } returns "inner"
        if (Service.viaInherited() != "inner") return@lokkiScope "Fail call from inside the object"
        verify(exactly = 3) { Service.greet("a") }
        every { Service.mode } returns "sport"
        if (Service.mode != "sport") return@lokkiScope "Fail inherited property"
        every { Service.mode = any() } answers { }
        Service.mode = "x"
        verify { Service.mode = "x" }

        mockObject(WithDefault)
        every { WithDefault.fallback() } returns "stubbed"
        if (WithDefault.fallback() != "stubbed") return@lokkiScope "Fail default method"

        mockObject(Typed)
        every { Typed.echo(1) } returns 2
        if (Typed.echo(1) != 2) return@lokkiScope "Fail generic superclass"
        if (Typed.echo(3) != 3) return@lokkiScope "Fail original of a generic superclass"
        "OK"
    }
