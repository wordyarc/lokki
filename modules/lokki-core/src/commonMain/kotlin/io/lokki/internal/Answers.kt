package io.lokki.internal

import io.lokki.Answer
import io.lokki.Call
import io.lokki.LokkiException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

internal class ConstantAnswer(val value: Any?) : Answer<Any?> {
    override fun answer(call: Call): Any? = value
}

internal class ThrowingAnswer(val exception: Throwable) : Answer<Nothing> {
    override fun answer(call: Call): Nothing = throw exception
}

internal class ArgumentAnswer(val index: Int) : Answer<Any?> {
    override fun answer(call: Call): Any? = call.invocation.args[index]
}

internal class SuspendingAnswer<T>(private val block: suspend (Call) -> T) : Answer<T> {
    override fun answer(call: Call): T {
        val result = block.startCoroutineUninterceptedOrReturn(call, Abandoned)
        if (result === COROUTINE_SUSPENDED) {
            throw LokkiException(
                "coAnswers suspended in non-suspend ${call.invocation.describe()}; " +
                    "an answer of a non-suspend member must complete without suspension"
            )
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    override suspend fun coAnswer(call: Call): T = block(call)
}

private object Abandoned : Continuation<Any?> {
    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Any?>) {}
}
