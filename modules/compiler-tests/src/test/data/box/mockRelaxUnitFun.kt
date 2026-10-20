package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify

interface Sink {
    var level: Int
    fun write(value: String)
    fun size(): Int
    fun flush(): Unit?
}

interface Box<T> {
    fun get(): T
}

open class Worker {
    open fun run() {
        throw IllegalStateException("original")
    }
}

private fun answer(block: () -> Any?): String =
    try {
        "returned ${block()}"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(\w*#\d+\)"""), "")
    }

fun box(): String {
    val sink = mock<Sink>(relaxUnitFun = true)
    sink.write("a")
    sink.level = 3
    val strict = listOf(answer { sink.size() }, answer { sink.level }, answer { sink.flush() })
    val expected = listOf(
        "no answer found for Sink.size() among the configured answers: ()\nCalled member: Sink.size(): Int",
        "no answer found for Sink.getLevel() among the configured answers: ()\nCalled member: Sink.getLevel(): Int",
        "no answer found for Sink.flush() among the configured answers: ()\nCalled member: Sink.flush(): Unit?",
    )
    if (strict != expected) return "Fail strict members: $strict"
    verify {
        sink.write("a")
        sink.level = 3
    }

    val named = mock<Sink>("sink", relaxUnitFun = true) {
        every { size() } returns 1
    }
    named.write("b")
    if (named.size() != 1 || !named.toString().startsWith("Sink(sink#")) return "Fail name and block"

    mock<() -> Unit>(relaxUnitFun = true)()
    mock<Box<Unit>>(relaxUnitFun = true).get()
    mock<Worker>(relaxUnitFun = true).run()
    if (mock<Sink>(relaxed = true, relaxUnitFun = true).size() != 0) return "Fail relaxed with relaxUnitFun"

    val order = ArrayList<String>()
    fun flag(name: String, value: Boolean): Boolean {
        order += name
        return value
    }
    val dynamic = mock<Sink>(
        relaxUnitFun = flag("relaxUnitFun", true),
        name = "dynamic".also { order += "name" },
        relaxed = flag("relaxed", false),
    )
    if (order != listOf("relaxUnitFun", "name", "relaxed")) return "Fail evaluation order: $order"
    dynamic.write("c")
    val dynamicAnswer = answer { dynamic.size() }
    if (dynamicAnswer != expected.first()) {
        return "Fail dynamic flags: $dynamicAnswer"
    }
    val relaxed = mock<Sink>(relaxed = flag("relaxed", true))
    if (relaxed.size() != 0) return "Fail dynamic relaxed"

    return "OK"
}
