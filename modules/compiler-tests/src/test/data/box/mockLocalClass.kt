package sample

import io.lokki.every
import io.lokki.mock

fun box(): String {
    open class Counter(val start: Int) {
        open fun next(): Int = start + 1
    }

    val counter = mock<Counter>()
    every { counter.next() } returns 42
    if (counter.next() != 42 || counter.start != 0) return "Fail: local class"

    val base = 10

    abstract class Offset {
        abstract fun shift(): Int

        fun value(): Int = base + shift()
    }

    val offset = run { mock<Offset>() }
    every { offset.shift() } returns 1
    if (offset.value() != 1) return "Fail: a captured variable is initialized without a constructor: ${offset.value()}"

    return "OK"
}
