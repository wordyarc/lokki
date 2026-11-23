// WITH_STDLIB
package sample

import io.lokki.coEvery
import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.reflect.KFunction1
import kotlin.reflect.KSuspendFunction1

fun square(value: Int) = value * value

suspend fun cube(value: Int) = value * value * value

fun <T> complete(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

fun box(): String {
    val mocked = mock<KFunction1<Int, Int>>()
    every { mocked(3) } returns 10
    if (mocked(3) != 10) return "Fail mock of KFunction1"
    if (listOf(3).map(mocked) != listOf(10)) return "Fail KFunction1 as a function value"
    verify(exactly = 2) { mocked(3) }

    val spied = spy(::square)
    if (spied(3) != 9) return "Fail spy of a function reference: ${spied(3)}"
    every { spied(4) } returns -1
    if (spied(4) != -1) return "Fail stub of a function reference spy"
    verify(exactly = 1) { spied(4) }

    val suspending = mock<KSuspendFunction1<Int, Int>>()
    coEvery { suspending(2) } returns 20
    if (complete { suspending(2) } != 20) return "Fail mock of KSuspendFunction1"

    val spiedSuspending = spy(::cube)
    if (complete { spiedSuspending(2) } != 8) return "Fail spy of a suspend function reference"

    return "OK"
}
