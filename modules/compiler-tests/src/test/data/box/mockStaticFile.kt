// MODULE: main
// INTERCEPTABLE: sample.**
// FILE: cars.kt
package sample

fun plain(): String = "plain"

fun count(x: Int): String = "int$x"

fun count(x: String): String = "string$x"

fun <T> echo(value: T): T = value

val color: String
    get() = "red"

fun countViaFile(): String = count(1)

// FILE: other.kt
package sample

fun other(): String = "other"

// FILE: first.kt
@file:JvmMultifileClass
@file:JvmName("Utils")

package sample

fun first(): String = "first"

// FILE: second.kt
@file:JvmMultifileClass
@file:JvmName("Utils")

package sample

fun second(): String = "second"

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mockStatic
import io.lokki.unmockStatic
import io.lokki.verify
import sample.color
import sample.count
import sample.countViaFile
import sample.echo
import sample.first
import sample.other
import sample.plain
import sample.second

fun box(): String {
    val result = lokkiScope {
        mockStatic("sample.CarsKt")
        if (plain() != "plain") return@lokkiScope "Fail unstubbed function runs the original"
        verify { plain() }
        every { count(1) } returns "one"
        every { count("a") } returns "letter"
        every { echo(1) } returns 2
        every { color } returns "blue"
        if (count(1) != "one" || count("a") != "letter") return@lokkiScope "Fail overloads"
        if (countViaFile() != "one") return@lokkiScope "Fail call from main"
        if (echo(1) != 2) return@lokkiScope "Fail generic"
        if (color != "blue") return@lokkiScope "Fail property"
        verify(exactly = 2) { count(1) }
        unmockStatic("sample.CarsKt")
        if (count(1) != "int1" || color != "red") return@lokkiScope "Fail unmockStatic"

        mockStatic("sample.Utils")
        every { first() } returns "1"
        every { second() } returns "2"
        if (first() != "1" || second() != "2") return@lokkiScope "Fail multifile class"
        unmockStatic("sample.Utils")
        if (first() != "first") return@lokkiScope "Fail unmock of a multifile class"

        var inside = ""
        mockStatic("sample.OtherKt") {
            every { other() } returns "mocked"
            inside = other()
        }
        if (inside != "mocked" || other() != "other") return@lokkiScope "Fail block form"
        "OK"
    }
    return result
}
