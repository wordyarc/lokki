// FILE: test.kt
@file:RelaxedChildMocks

package sample

import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.RelaxedChildMocks
import io.lokki.every
import io.lokki.mock
import io.lokki.mockClass
import io.lokki.verify

sealed interface Shape {
    fun area(): Int
}

class Circle : Shape {
    override fun area(): Int = 1
}

open class Square : Shape {
    override fun area(): Int = 2
}

sealed class Outcome<out T> {
    abstract fun value(): T?
}

object Failure : Outcome<Nothing>() {
    override fun value(): Nothing? = null
}

open class Success<T>(private val result: T) : Outcome<T>() {
    override fun value(): T? = result
}

sealed class Expression<T> {
    abstract fun evaluate(): T
}

open class IntExpression : Expression<Int>() {
    override fun evaluate(): Int = 1
}

open class TextExpression : Expression<String>() {
    override fun evaluate(): String = "real"
}

sealed class Tree {
    abstract fun size(): Int

    sealed class Branch : Tree()

    open class Leaf : Branch() {
        override fun size(): Int = 1
    }
}

interface Canvas {
    fun shape(): Shape
    fun outcome(): Outcome<String>
}

class Holder {
    @Mock
    lateinit var shape: Shape
}

fun box(): String {
    val shape = mock<Shape>()
    every { shape.area() } returns 7
    if (shape.area() != 7) return "shape"
    val kind = when (shape) {
        is Circle -> "circle"
        is Square -> "square"
    }
    if (kind != "square") return kind
    verify { shape.area() }

    val outcome = mock<Outcome<String>>()
    every { outcome.value() } returns "stub"
    if (outcome.value() != "stub") return "outcome"
    if (outcome !is Success) return "generic subclass"

    val expression = mock<Expression<String>>()
    every { expression.evaluate() } returns "text"
    if (expression.evaluate() != "text") return "expression"
    if (expression !is TextExpression) return "subclass with the requested type argument"

    val tree = mock<Tree>()
    every { tree.size() } returns 3
    if (tree.size() != 3 || tree !is Tree.Leaf) return "nested sealed class"

    if (mockClass(Shape::class) !is Square) return "mockClass"

    val canvas = mock<Canvas>()
    every { canvas.shape().area() } returns 9
    if (canvas.shape().area() != 9) return "link"

    val relaxed = mock<Canvas>(relaxed = true)
    if (relaxed.shape().area() != 0) return "relaxed child"
    if (relaxed.outcome() !is Success) return "relaxed generic child"

    val holder = Holder()
    MockAnnotations.init(holder)
    every { holder.shape.area() } returns 11
    if (holder.shape.area() != 11) return "field"
    return "OK"
}
