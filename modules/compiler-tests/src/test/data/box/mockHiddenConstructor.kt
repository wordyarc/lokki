// WITH_STDLIB
// MOCKABLE: lib.Token

// MODULE: lib
// FILE: lib/Types.kt
package lib

class Token private constructor(val value: String) {
    fun read(): String = value

    companion object {
        fun create(value: String) = Token(value)
    }
}

open class Registry internal constructor(private val name: String) {
    open fun find(id: Int): String = "$name$id"
}

abstract class Singleton private constructor() {
    abstract fun value(): Int

    object Instance : Singleton() {
        override fun value(): Int = 1
    }
}

interface Source {
    fun registry(): Registry
}

sealed interface Shape

open class Square private constructor() : Shape {
    open fun sides(): Int = 4
}

// FILE: lib/Packaged.java
package lib;

public class Packaged {
    Packaged() {
        throw new IllegalStateException("constructor ran");
    }

    public String name() {
        return "real";
    }
}

// MODULE: main(lib)
// FILE: main.kt
package sample

import io.lokki.every
import io.lokki.isMock
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import lib.Packaged
import lib.Registry
import lib.Shape
import lib.Singleton
import lib.Source
import lib.Square
import lib.Token

fun box(): String {
    val token = mock<Token>()
    every { token.read() } returns "stub"
    if (token.read() != "stub") return "Fail private constructor of an opened class"
    verify { token.read() }

    val registry = mock<Registry>()
    every { registry.find(1) } returns "stub"
    if (registry.find(1) != "stub") return "Fail internal constructor of another module"

    val singleton = mock<Singleton>()
    every { singleton.value() } returns 2
    if (singleton.value() != 2) return "Fail private constructor of an abstract class"

    val packaged = mock<Packaged>()
    every { packaged.name() } returns "stub"
    if (packaged.name() != "stub") return "Fail package-private Java constructor"

    val source = mock<Source>()
    every { source.registry().find(any()) } returns "chained"
    if (source.registry().find(5) != "chained") return "Fail child mock without a visible constructor"

    val shape = mock<Shape>()
    if (shape !is Square) return "Fail sealed type through a subclass without a visible constructor"
    every { shape.sides() } returns 3
    if (shape.sides() != 3) return "Fail stub of the sealed subclass"

    val delegating = spy(Token.create("real"))
    if (delegating.read() != "real") return "Fail delegating spy"
    every { delegating.read() } returns "spied"
    if (delegating.read() != "spied" || !isMock(delegating, regular = false, spy = true)) return "Fail spy stub"

    return "OK"
}
