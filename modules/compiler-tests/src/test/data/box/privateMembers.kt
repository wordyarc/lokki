// MODULE: main
// MOCKABLE: sample.**
// FILE: members.kt
package sample

class Abc {
    fun y() = x()

    private fun x() = "abc"
}

object Def {
    fun y() = x()

    private fun x() = "abc"
}

open class Parent {
    open fun call(): String = hidden()

    private fun hidden() = "parent"
}

class Child : Parent() {
    fun own(): String = hidden()

    private fun hidden() = "child"
}

class Generic {
    private fun <T> update(id: Long, data: T) {
        seen = "$id $data"
    }

    var seen = ""

    fun call() = update(1L, "data")
}

class Recursive {
    fun run(n: Int) = count(n)

    private fun count(n: Int): Int = if (n == 0) 0 else 1 + count(n - 1)
}

class Defaults {
    fun y() = x()

    private fun x(a: Int = 7) = a
}

class Suspending {
    suspend fun y() = x()

    private suspend fun x() = "real"
}

class Unit0 {
    var called = false

    fun run() = act()

    private fun act() {
        called = true
    }
}

// MODULE: test(main)
// FILE: test.kt
import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.coEvery
import io.lokki.every
import io.lokki.just
import io.lokki.justRun
import io.lokki.lokkiScope
import io.lokki.mock
import io.lokki.mockObject
import io.lokki.spy
import io.lokki.verify
import io.lokki.verifySequence
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import sample.*

private fun <T> blocking(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

fun box(): String = lokkiScope {
    val recorded = spy<Abc>(recordPrivateCalls = true)
    every { recorded["x"]() } returns "def"
    if (recorded.y() != "def") return@lokkiScope "stub"
    verifySequence {
        recorded.y()
        recorded["x"]()
    }

    val silent = spy<Abc>()
    every { silent["x"]() } returns "def"
    if (silent.y() != "def") return@lokkiScope "stub without recording"
    verifySequence { silent.y() }

    if (spy<Abc>().y() != "abc") return@lokkiScope "original of a spy"
    if (Abc().y() != "abc") return@lokkiScope "plain instance"

    val strict = mock<Abc>()
    every { strict.y() } answers { callOriginal() }
    val failure = runCatching { strict.y() }.exceptionOrNull()
    if (failure !is LokkiException || "no answer" !in failure.message.orEmpty()) return@lokkiScope "strict: $failure"
    io.lokki.clearMocks(strict)

    val relaxed = mock<Abc>(relaxed = true)
    every { relaxed.y() } answers { callOriginal() }
    if (relaxed.y() != "") return@lokkiScope "relaxed"

    mockObject(Def, recordPrivateCalls = true) {
        every { Def["x"]() } returns "ghi"
        if (Def.y() != "ghi") return@lokkiScope "object"
        verify {
            Def.y()
            Def["x"]()
        }
    }
    if (Def.y() != "abc") return@lokkiScope "object after the scope"

    val inherited = mock<Parent> {
        every { call() } answers { callOriginal() }
        every { this@mock["hidden"]() } returns "mock"
    }
    if (inherited.call() != "mock") return@lokkiScope "private of a parent"

    val child = spy(Child())
    every { child["hidden"]() } returns "stub"
    if (child.own() != "stub") return@lokkiScope "most derived"
    if (child.call() != "parent") return@lokkiScope "parent keeps its own: ${child.call()}"

    val generic = spy<Generic>()
    every { generic["update"](any<Long>(), any<String>()) as Unit } just Runs
    generic.call()
    if (generic.seen != "") return@lokkiScope "generic"

    val recursive = spy(Recursive(), recordPrivateCalls = true)
    if (recursive.run(2) != 2) return@lokkiScope "recursion"
    verify(exactly = 1) { recursive["count"](any<Int>()) }

    val defaults = spy<Defaults>()
    every { defaults["x"](7) } returns 1
    if (defaults.y() != 1) return@lokkiScope "default argument"

    val suspending = spy<Suspending>()
    coEvery { suspending["x"]() } returns "stub"
    if (blocking { suspending.y() } != "stub") return@lokkiScope "suspend"

    val unit = spy<Unit0>()
    justRun { unit invokeNoArgs "act" }
    unit.run()
    if (unit.called) return@lokkiScope "justRun"

    val delegating = Abc()
    val failed = runCatching {
        val delegate = spy(delegating)
        every { delegate["x"]() } returns "stub"
    }.exceptionOrNull()
    if (failed !is LokkiException || "delegates" !in failed.message.orEmpty()) return@lokkiScope "delegating spy: $failed"
    "OK"
}
