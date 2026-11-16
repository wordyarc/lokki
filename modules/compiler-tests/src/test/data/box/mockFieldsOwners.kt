package app

import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.every

interface Car {
    fun drive(): String
}

object Garage {
    @Mock
    lateinit var car: Car
}

class Outer {
    @Mock
    lateinit var car: Car

    inner class Inner {
        @Mock
        lateinit var car: Car
    }

    class Nested {
        @Mock
        lateinit var car: Car
    }

    companion object {
        @Mock
        lateinit var shared: Car
    }
}

abstract class Base {
    @Mock
    lateinit var baseCar: Car
}

open class Middle : Base()

class Leaf : Middle() {
    @Mock
    lateinit var leafCar: Car
}

fun drives(car: Car, answer: String): Boolean {
    every { car.drive() } returns answer
    return car.drive() == answer
}

fun box(): String {
    class Local {
        @Mock
        lateinit var car: Car
    }

    val local = Local()
    MockAnnotations.init(local)
    if (!drives(local.car, "local")) return "Fail local"

    MockAnnotations.init(Garage)
    if (!drives(Garage.car, "object")) return "Fail object"

    val outer = Outer()
    val inner = outer.Inner()
    val nested = Outer.Nested()
    MockAnnotations.init(outer, inner, nested, Outer)
    if (!drives(outer.car, "outer")) return "Fail outer"
    if (!drives(inner.car, "inner")) return "Fail inner"
    if (!drives(nested.car, "nested")) return "Fail nested"
    if (!drives(Outer.shared, "companion")) return "Fail companion"

    val leaf = Leaf()
    MockAnnotations.init(leaf)
    if (!drives(leaf.baseCar, "base")) return "Fail base"
    if (!drives(leaf.leafCar, "leaf")) return "Fail leaf"

    return "OK"
}
