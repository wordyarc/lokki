package sample

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.slot

interface Shape

class Circle(val radius: Int) : Shape

class Square(val side: Int) : Shape

interface Canvas {
    fun draw(shape: Shape, layer: Int): String
    fun label(text: String?, size: Int): String
    fun tags(vararg tags: String): String
    fun count(): Int
}

private fun answer(block: () -> String): String =
    try {
        block()
    } catch (e: LokkiException) {
        "none"
    }

fun box(): String {
    val canvas = mock<Canvas>()

    val shape = slot<Shape>()
    val layers = ArrayList<Int>()
    every { canvas.draw(capture(shape), capture(layers)) } returns "drawn"
    if (shape.isCaptured || layers.isNotEmpty()) return "Fail: captured at registration"
    val circle = Circle(1)
    canvas.draw(circle, 1)
    canvas.draw(Square(2), 2)
    if (shape.captured !is Square || layers != listOf(1, 2)) return "Fail: slot keeps the last value, list keeps all"

    val circles = ArrayList<Circle>()
    every { canvas.draw(capture(circles), 10) } returns "circle"
    if (canvas.draw(circle, 10) != "circle") return "Fail: capture of a subtype"
    if (canvas.draw(Square(1), 10) != "drawn") return "Fail: capture of a subtype matched another subtype"
    if (circles != listOf(circle)) return "Fail: captured subtypes $circles"
    if (layers != listOf(1, 2, 10)) return "Fail: capture of the stub that answered"

    val partial = ArrayList<Shape>()
    every { canvas.draw(capture(partial), 20) } returns "layer 20"
    canvas.draw(circle, 21)
    if (partial.isNotEmpty()) return "Fail: captured when another argument did not match"

    val text = slot<String>()
    every { canvas.label(capture(text), 1) } returns "text"
    if (canvas.label(null, 1) != "text" || text.isCaptured) return "Fail: capture skips null"
    canvas.label("a", 1)
    if (text.captured != "a") return "Fail: capture of a non-null value"

    val nullableText = CapturingSlot<String?>()
    val nullableTexts = ArrayList<String?>()
    every { canvas.label(captureNullable(nullableText), 2) } returns "nullable"
    every { canvas.label(captureNullable(nullableTexts), 3) } returns "nullables"
    canvas.label(null, 2)
    canvas.label("b", 3)
    canvas.label(null, 3)
    if (!nullableText.isNull || nullableTexts != listOf("b", null)) return "Fail: captureNullable"

    val small = ArrayList<Int>()
    every { canvas.label("and", and(capture(small), less(5))) } returns "small"
    canvas.label("and", 3)
    if (answer { canvas.label("and", 7) } != "none" || small != listOf(3)) return "Fail: capture in a composite $small"

    val first = slot<String>()
    every { canvas.tags(capture(first), any()) } returns "tags"
    canvas.tags("x", "y")
    if (first.captured != "x") return "Fail: capture of a vararg element"

    every { canvas.count() } returnsMany listOf(1, 2, 3)
    val counts = ArrayList<Int>()
    every { canvas.label("count", capture(counts)) } returns "counted"
    every { canvas.label("registered", canvas.count()) } returns "one"
    if (canvas.label("registered", 1) != "one") return "Fail: value of a mock call at registration"
    every { canvas.draw(circle, canvas.label("count", 5).length) } returns "nested"
    if (counts.isNotEmpty()) return "Fail: captured by a call at registration"
    if (List(3) { canvas.count() } != listOf(1, 2, 3)) return "Fail: registration moved the answers"

    return "OK"
}
