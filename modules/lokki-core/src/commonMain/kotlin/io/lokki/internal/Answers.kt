package io.lokki.internal

import io.lokki.Answer
import io.lokki.Call

internal class ConstantAnswer(val value: Any?) : Answer<Any?> {
    override fun answer(call: Call): Any? = value
}

internal class ThrowingAnswer(val exception: Throwable) : Answer<Nothing> {
    override fun answer(call: Call): Nothing = throw exception
}

internal class ArgumentAnswer(val index: Int) : Answer<Any?> {
    override fun answer(call: Call): Any? = call.invocation.args[index]
}
