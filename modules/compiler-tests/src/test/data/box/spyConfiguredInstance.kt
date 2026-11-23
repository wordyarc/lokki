// WITH_STDLIB
package sample

import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify

open class Counter(val step: Int = 1) {
    open var hits: Int = 0

    val report by lazy { "hits=$hits" }

    open fun bump() {
        hits += step
    }
}

open class Box<T>(open var value: T)

interface Listener {
    fun called(): Int
}

var order = ""

fun step(name: String): Int {
    order += name
    return 1
}

fun box(): String {
    val applied = spy(Counter().apply { hits = 42 })
    if (applied.javaClass.superclass != Counter::class.java) return "Fail apply is a delegating spy"
    if (applied.hits != 42) return "Fail state of apply: ${applied.hits}"
    every { applied.hits } returns 7
    if (applied.report != "hits=7") return "Fail stub is not seen by own code: ${applied.report}"

    val silent = spy(Counter().apply { bump() })
    confirmVerified(silent)
    if (silent.hits != 1) return "Fail member called in apply: ${silent.hits}"

    var seen: Counter? = null
    val also = spy(Counter(2).also {
        seen = it
        it.bump()
    })
    confirmVerified(also)
    if (seen !== also || also.hits != 2) return "Fail also"

    val chained = spy(Counter().apply { hits = 1 }.also { it.hits += 1 }.apply { bump() })
    confirmVerified(chained)
    if (chained.hits != 3) return "Fail chain of scope functions: ${chained.hits}"

    val generic = spy(Box("a").apply { value = "b" })
    if (generic.value != "b") return "Fail generic"

    val named = spy(name = "named", obj = Counter().apply { hits = 5 })
    if (named.hits != 5 || !named.toString().startsWith("Counter(named#")) return "Fail named: $named"

    val reordered = spy(Counter(step = step("a")).apply { step("b") }, block = { step("c") })
    if (order != "abc" || reordered.step != 1) return "Fail evaluation order: $order"

    val listener = mock<Listener>()
    every { listener.called() } returns 1
    spy(Counter().apply { hits = listener.called() })
    verify(exactly = 1) { listener.called() }

    val after = spy(Counter().apply { hits = 1 })
    after.bump()
    verify(exactly = 1) { after.bump() }

    return "OK"
}
