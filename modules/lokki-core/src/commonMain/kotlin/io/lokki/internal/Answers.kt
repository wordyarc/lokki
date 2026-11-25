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
    val state: MockState? = (value as? GeneratedMock)?.`lokki$state`()

    override fun answer(call: Call): Any? = value

    override fun toString(): String = "returns ${value.toStr()}"
}

internal class ThrowingAnswer(val exception: Throwable) : Answer<Nothing> {
    override fun answer(call: Call): Nothing = throw exception

    override fun toString(): String = "throws $exception"
}

internal class ArgumentAnswer(val index: Int) : Answer<Any?> {
    override fun answer(call: Call): Any? = call.invocation.args[index]

    override fun toString(): String = "returnsArgument $index"
}

internal class FunctionAnswer<T>(private val block: (Call) -> T) : Answer<T> {
    override fun answer(call: Call): T = block(call)

    override fun toString(): String = "answers { }"
}

internal class SuspendingAnswer<T>(private val block: suspend (Call) -> T) : Answer<T> {
    override fun answer(call: Call): T {
        val result = block.startCoroutineUninterceptedOrReturn(call, Abandoned)
        if (result === COROUTINE_SUSPENDED) {
            throw LokkiException(
                "the answer of non-suspend ${call.invocation.describe()} suspended: an answer of a non-suspend " +
                    "member, such as coAnswers, coAndThen or just Awaits, must complete without suspension"
            )
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    override suspend fun coAnswer(call: Call): T = block(call)

    override fun toString(): String = "coAnswers { }"
}

internal object Abandoned : Continuation<Any?> {
    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Any?>) {}
}
