package sample

import io.lokki.every
import io.lokki.mock

abstract class Box<T> {
    abstract fun get(): T
}

fun box(): String {
    class Outer {
        open inner class Inner {
            open fun id(): Int = 1
        }
    }

    val inner = mock<Outer.Inner>()
    every { inner.id() } returns 2
    if (inner.id() != 2) return "Fail: inner class of a local class"

    val made = object {
        open inner class Part {
            open fun id(): Int = 1
        }

        fun stubbed(): Int {
            val part = mock<Part>()
            every { part.id() } returns 3
            return part.id()
        }
    }
    if (made.stubbed() != 3) return "Fail: inner class of an anonymous object"

    val first = run {
        open class Handler {
            open fun handle(): Int = 1
        }

        val handler = mock<Handler>()
        every { handler.handle() } returns 4
        handler.handle()
    }
    val second = run {
        open class Handler {
            open fun handle(): String = "original"
        }

        val handler = mock<Handler>()
        every { handler.handle() } returns "stubbed"
        handler.handle()
    }
    if (first != 4 || second != "stubbed") return "Fail: local classes with the same name: $first, $second"

    val number = run {
        class Value(val number: Int)

        val box = mock<Box<Value>>()
        every { box.get() } returns Value(5)
        box.get().number
    }
    val text = run {
        class Value(val text: String)

        val box = mock<Box<Value>>()
        every { box.get() } returns Value("text")
        box.get().text
    }
    if (number != 5 || text != "text") return "Fail: local type arguments with the same name: $number, $text"

    return "OK"
}
