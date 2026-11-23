package sample

import io.lokki.Answer
import io.lokki.Call
import io.lokki.every
import io.lokki.mock
import io.lokki.spy

open class Prop {
    open var value: Int = 0

    open fun twice(x: Int): Int = x * 2
}

fun box(): String {
    val prop = mock<Prop>()
    var stored: Any? = null
    every { prop.value = any() } answers { stored = valueAny }
    prop.value = 5
    if (stored != 5) return "Fail valueAny"

    val spied = spy(Prop())
    every { spied.twice(any()) } answers(object : Answer<Int> {
        override fun answer(call: Call): Int = call.invocation.originalCall() as Int + 1
    })
    if (spied.twice(2) != 5) return "Fail originalCall"

    var first: Call? = null
    every { spied.twice(7) } answers { call -> first = call; 0 }
    spied.twice(7)
    val call = first!!
    if ("twice(7)" !in call.invocation.toString()) return "Fail Invocation.toString: ${call.invocation}"
    if (call != call || call.hashCode() != call.hashCode()) return "Fail Call.equals"
    return if ("invocation=" in call.toString()) "OK" else "Fail Call.toString: $call"
}
