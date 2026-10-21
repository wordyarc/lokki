// WITH_STDLIB
package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface A {
    fun method1(items: List<Int>)

    fun method2(items: List<Int>)

    fun count(): Int

    fun defaultMethod(callMethod2: Boolean) {
        method1(listOf(1, 2, 3))
        if (callMethod2) method2(listOf(4, 5, 6))
    }

    suspend fun load(): Int = count() + 1
}

fun <T> run(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

fun box(): String {
    val spy = spy<A>()
    if (spy.javaClass != mock<A>().javaClass) return "Fail class: ${spy.javaClass.name}"

    spy.defaultMethod(callMethod2 = true)
    verify { spy.method1(listOf(1, 2, 3)) }
    verify { spy.method2(listOf(4, 5, 6)) }

    val failure = try {
        spy.count()
        null
    } catch (e: LokkiException) {
        e.message
    }
    if (failure?.startsWith("no original implementation for A(#") != true) return "Fail abstract member: $failure"

    every { spy.count() } returns 3
    if (run { spy.load() } != 4) return "Fail suspend default method"

    if (!spy.toString().startsWith("A(#") || spy != spy || spy == spy<A>()) return "Fail methods of Any: $spy"
    if (spy.hashCode() != System.identityHashCode(spy)) return "Fail hashCode"

    val configured = spy<A> {
        every { count() } returns 7
    }
    if (configured.count() != 7) return "Fail block"

    val function = spy<(Int) -> Unit>()
    function(5)
    verify { function(5) }

    return "OK"
}
