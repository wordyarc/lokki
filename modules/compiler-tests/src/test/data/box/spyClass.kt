// WITH_STDLIB
package sample

import io.lokki.AnswerScope
import io.lokki.LokkiException
import io.lokki.every
import io.lokki.isMock
import io.lokki.spy
import io.lokki.verify
import java.io.Closeable
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

open class Counter(val start: Int, val step: Int = 1) {
    var resets = 0

    init {
        reset()
    }

    open fun reset() {
        resets++
    }

    open fun next(): Int = start + step

    fun twice(): Int = next() + next()

    open fun page(n: Int, size: Int = start * 10): String = "page $n of $size"

    open suspend fun load(n: Int): Int = suspendCoroutine { it.resume(n + start) }

    override fun toString(): String = "Counter($start)"

    override fun equals(other: Any?): Boolean = other is Counter && other.start == start

    override fun hashCode(): Int = start
}

open class Pager(val size: Int = 20) {
    open fun size(): Int = size
}

abstract class Template {
    init {
        name()
    }

    abstract fun name(): String
}

abstract class Shape {
    abstract fun area(): Int

    open fun describe(): String = "area ${area()}"

    abstract fun scale(factor: Int)
}

fun AnswerScope<Int>.original(): Int = callOriginal()

fun <T> run(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

fun box(): String {
    val counter = spy(Counter(5))
    val spyClass = counter.javaClass

    if (spyClass.name != "sample.Counter\$LokkiSpy\$a8d358e8\$SpyClassKt") return "Fail name: ${spyClass.name}"
    if (spyClass.superclass != Counter::class.java) return "Fail superclass: ${spyClass.superclass}"
    if (!isMock(counter)) return "Fail isMock"
    val state = spyClass.getDeclaredField("lokki\$state")
    if (!Modifier.isFinal(state.modifiers) || Modifier.isVolatile(state.modifiers)) return "Fail state field"

    if (counter.resets != 1) return "Fail constructor call: ${counter.resets}"
    if (counter.next() != 6) return "Fail original: ${counter.next()}"
    if (counter.twice() != 12) return "Fail original self-call"
    every { counter.next() } returns 10
    if (counter.twice() != 20) return "Fail stubbed self-call: ${counter.twice()}"
    verify(exactly = 5) { counter.next() }
    verify(exactly = 0) { counter.reset() }
    counter.reset()
    if (counter.resets != 2) return "Fail reset: ${counter.resets}"
    verify(exactly = 1) { counter.reset() }

    every { counter.next() } answers { callOriginal() * 100 }
    if (counter.next() != 600) return "Fail callOriginal: ${counter.next()}"

    if (!counter.toString().startsWith("Counter(#")) return "Fail toString: $counter"
    if (counter != Counter(5) || counter.hashCode() != 5) return "Fail equals: ${counter.hashCode()}"
    if (counter == Counter(6)) return "Fail not equals"

    every { counter.page(1) } returns "stubbed"
    if (counter.page(1, 50) != "stubbed") return "Fail default of the spy: ${counter.page(1, 50)}"
    if (counter.page(1, 10) != "page 1 of 10") return "Fail other default: ${counter.page(1, 10)}"

    if (run { counter.load(1) } != 6) return "Fail suspend original"
    every { counter.next() } answers { 0 }
    try {
        Class.forName(spyClass.name + "\$load\$1")
        return "Fail continuation class of load"
    } catch (_: ClassNotFoundException) {
    }

    val pager = spy<Pager>()
    if (pager.size() != 20) return "Fail no-argument constructor: ${pager.size()}"
    if (spy(Pager()).javaClass != pager.javaClass) return "Fail class of the same shape"
    if (spy(Pager(30)).javaClass == pager.javaClass) return "Fail class of another shape"
    val sized = spy(Pager(30)) {
        every { size() } returns 31
    }
    if (sized.size() != 31 || sized.size != 30) return "Fail block: ${sized.size()}"

    val named = spy(Pager(7), name = "pager")
    if (!named.toString().startsWith("Pager(pager#")) return "Fail name: $named"

    val failure = try {
        spy<Template>()
        null
    } catch (e: LokkiException) {
        e.message
    }
    if (failure != "abstract Template.name was called by a constructor of Template before the spy was created") {
        return "Fail abstract member in constructor: $failure"
    }

    val shape = spy<Shape>()
    every { shape.area() } returns 4
    if (shape.describe() != "area 4") return "Fail abstract member: ${shape.describe()}"
    shape.scale(2)
    verify { shape.scale(2) }
    val noOriginal = try {
        every { shape.area() } answers { original() }
        shape.area()
        null
    } catch (e: LokkiException) {
        e.message
    }
    if (noOriginal?.startsWith("no original implementation for Shape(#") != true) return "Fail no original: $noOriginal"

    val closeable = spy(Counter(1), moreInterfaces = arrayOf(Closeable::class))
    (closeable as Closeable).close()
    verify { closeable.close() }

    return "OK"
}
