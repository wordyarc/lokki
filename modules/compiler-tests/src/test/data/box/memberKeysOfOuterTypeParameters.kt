package sample

import io.lokki.every
import io.lokki.mock

class Outer<A : CharSequence> {
    open inner class Inner<B : Number> {
        open fun same(x: A): String = "outer"
        open fun same(x: B): String = "inner"
    }
}

fun <T : CharSequence> local(): String {
    open class Local<B : Number> {
        open fun same(x: T): String = "function"
        open fun same(x: B): String = "class"
    }

    val local = mock<Local<Int>>()
    every { local.same(1) } returns "number"
    return local.same(1)
}

fun box(): String {
    val inner = mock<Outer<String>.Inner<Int>>()
    every { inner.same("a") } returns "text"
    every { inner.same(1) } returns "number"
    if (inner.same("a") != "text") return "Fail: parameter of the outer class"
    if (inner.same(1) != "number") return "Fail: parameter of the inner class"
    if (local<String>() != "number") return "Fail: parameter of the enclosing function"
    return "OK"
}
