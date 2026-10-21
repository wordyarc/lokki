// MODULE: base
// FILE: Base.kt
package base

import io.lokki.Mock

interface Car {
    fun drive(): String
}

abstract class BaseTest {
    @Mock
    lateinit var baseCar: Car
}

open class WithoutFields : BaseTest()

// MODULE: middle(base)
// FILE: Middle.kt
package middle

import base.BaseTest
import base.Car
import io.lokki.Mock

open class MiddleTest : BaseTest() {
    @Mock
    lateinit var middleCar: Car
}

// MODULE: main(middle, base)
// FILE: main.kt
package app

import base.Car
import base.WithoutFields
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.isMock
import middle.MiddleTest

class LeafTest : MiddleTest() {
    @Mock
    lateinit var leafCar: Car
}

class DirectTest : WithoutFields() {
    @Mock(relaxed = true)
    lateinit var directCar: Car
}

class InheritedTest : MiddleTest()

fun box(): String {
    val leaf = LeafTest()
    MockAnnotations.init(leaf)
    if (!isMock(leaf.baseCar) || !isMock(leaf.middleCar) || !isMock(leaf.leafCar)) return "Fail: three modules"

    val direct = DirectTest()
    MockAnnotations.init(direct)
    if (!isMock(direct.baseCar) || direct.directCar.drive() != "") return "Fail: through a class without fields"

    val inherited = InheritedTest()
    MockAnnotations.init(inherited, relaxed = true)
    if (inherited.baseCar.drive() != "" || inherited.middleCar.drive() != "") return "Fail: flags reach the base"
    return "OK"
}
