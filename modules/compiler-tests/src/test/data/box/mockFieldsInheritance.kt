// FILE: Leaf.kt
package app

import io.lokki.Mock
import io.lokki.isMock

class Leaf : Middle() {
    @Mock
    lateinit var leafCar: Car

    fun initialized(): Boolean = isMock(baseCar) && isMock(leafCar)
}

class GenericLeaf : GenericBase<String>() {
    @Mock
    lateinit var leafCar: Car
}

class DirectLeaf : Base() {
    @Mock
    lateinit var directCar: Car
}

class OnlyInherited : Base()

// FILE: Base.kt
package app

import io.lokki.Mock

interface Car {
    fun drive(): String
}

interface Box<T> {
    fun get(): T
}

abstract class Base {
    @Mock
    lateinit var baseCar: Car
}

open class Middle : Base()

abstract class GenericBase<T> {
    @Mock
    lateinit var box: Box<T>
}

// FILE: main.kt
package app

import io.lokki.MockAnnotations
import io.lokki.every
import io.lokki.isMock

fun box(): String {
    val leaf = Leaf()
    MockAnnotations.init(leaf)
    if (!leaf.initialized()) return "Fail: leaf through a class without fields"

    val generic = GenericLeaf()
    MockAnnotations.init(generic)
    every { generic.box.get() } returns "boxed"
    if (generic.box.get() != "boxed" || !isMock(generic.leafCar)) return "Fail: generic base"

    val direct = DirectLeaf()
    MockAnnotations.init(direct)
    if (!isMock(direct.baseCar) || !isMock(direct.directCar)) return "Fail: direct subclass"

    val inherited = OnlyInherited()
    MockAnnotations.init(inherited)
    if (!isMock(inherited.baseCar)) return "Fail: subclass without fields"
    return "OK"
}
