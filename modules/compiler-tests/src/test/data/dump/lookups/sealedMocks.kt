// FILE: types.kt
package sample

sealed interface Shape {
    fun area(): Int
}

class Circle : Shape {
    override fun area(): Int = 1
}

open class Square : Shape {
    override fun area(): Int = 2
}

// FILE: SealedTest.kt
package sample

import io.lokki.mock

fun sealedMock(): Shape = mock<Shape>()
