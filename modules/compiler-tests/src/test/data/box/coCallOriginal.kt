package sample

import io.lokki.AnswerScope
import io.lokki.coEvery
import io.lokki.every
import io.lokki.mock
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

@JvmInline
value class Code(val value: Int)

@JvmInline
value class Name(val value: String)

private val waiting = ArrayList<() -> Unit>()
private var pauses = 0

suspend fun pause(): Unit = suspendCoroutine { continuation ->
    pauses++
    waiting += { continuation.resume(Unit) }
}

fun <T> complete(block: suspend () -> T): Result<T> {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    while (outcome == null) waiting.removeAt(0)()
    return outcome!!
}

private fun Result<*>.problem(): String? = exceptionOrNull()?.message?.replace(Regex("""\(#\d+\)"""), "")

val log = ArrayList<String>()

open class Engine {
    open suspend fun start(id: Int): String {
        pause()
        return "started $id"
    }

    open suspend fun speed(a: Int, b: Long): Long {
        pause()
        return a + b
    }

    open suspend fun code(): Code {
        pause()
        return Code(3)
    }

    open suspend fun maybe(): String? {
        pause()
        return null
    }

    open suspend fun <T : CharSequence> echo(value: T): T {
        pause()
        return value
    }

    open suspend fun record(message: String) {
        pause()
        log += message
    }

    open suspend fun String.shout(): String {
        pause()
        return uppercase()
    }

    open suspend fun twice(): String = start(1) + start(2)

    open suspend fun quick(): Int = 5

    open fun size(): Int = 4
}

open class Wrapper {
    open suspend fun name(): Name {
        pause()
        return Name("n")
    }

    open suspend fun result(): Result<String> {
        pause()
        return Result.success("r")
    }
}

interface Greeter {
    suspend fun name(): String

    suspend fun greet(): String {
        pause()
        return "hi ${name()}"
    }
}

abstract class Base : Greeter

interface Polite : Greeter

abstract class Pump {
    abstract suspend fun pressure(): Int
}

suspend fun AnswerScope<Int>.original(): Int = coCallOriginal()

fun AnswerScope<Int>.plainOriginal(): Int = callOriginal()

private fun Class<*>.hasNested(name: String): Boolean =
    runCatching { Class.forName("${this.name}\$$name", false, classLoader) }.isSuccess

fun box(): String {
    val engine = mock<Engine>()
    coEvery { engine.start(any()) } coAnswers { coCallOriginal() + "!" }
    coEvery { engine.speed(any(), any()) } coAnswers { coCallOriginal() * 10 }
    coEvery { engine.code() } coAnswers { coCallOriginal() }
    coEvery { engine.maybe() } coAnswers { coCallOriginal() ?: "none" }
    coEvery { engine.echo<String>(any()) } coAnswers { coCallOriginal() + "?" }
    coEvery { engine.record(any()) } coAnswers { coCallOriginal() }
    with(engine) { coEvery { any<String>().shout() } coAnswers { coCallOriginal() } }
    coEvery { engine.twice() } coAnswers { coCallOriginal() }
    pauses = 0
    val originals = complete {
        listOf(
            engine.start(1), engine.speed(2, 3L), engine.code(), engine.maybe(), engine.echo("e"), engine.record("rec"),
            with(engine) { "a".shout() },
        )
    }.getOrNull()
    val expected = listOf("started 1!", 50L, Code(3), "none", "e?", Unit, "A")
    if (originals != expected) return "Fail originals: $originals"
    if (pauses != expected.size) return "Fail: originals suspended $pauses times"
    if (log != listOf("rec")) return "Fail Unit member: $log"
    if (complete { engine.twice() }.getOrNull() != "started 1!started 2!") return "Fail: original calls the mock"

    coEvery { engine.quick() } answers { callOriginal() + 1 }
    if (complete { engine.quick() }.getOrNull() != 6) return "Fail callOriginal of a suspend member"
    coEvery { engine.start(7) } answers { callOriginal() }
    val suspended = complete { engine.start(7) }.problem()
    if (suspended != "callOriginal() of suspend Engine.start(7) suspended; use coCallOriginal()") {
        return "Fail suspension in callOriginal: $suspended"
    }
    waiting.clear()

    every { engine.size() } coAnswers { coCallOriginal() + 1 }
    if (engine.size() != 5) return "Fail coCallOriginal of a non-suspend member"

    coEvery { engine.start(8) } returns "stubbed" coAndThen { coCallOriginal() }
    if (complete { listOf(engine.start(8), engine.start(8)) }.getOrNull() != listOf("stubbed", "started 8")) {
        return "Fail coAndThen"
    }
    coEvery { engine.speed(9, any()) } coAnswers {
        pause()
        1L
    } coAndThen {
        pause()
        2L
    }
    if (complete { List(3) { engine.speed(9, 0L) } }.getOrNull() != listOf(1L, 2L, 2L)) return "Fail coAndThen chain"
    if (engine.javaClass.hasNested("lokki\$coOriginal\$1")) return "Fail: lokki\$coOriginal has a continuation"
    val dispatcher = engine.javaClass.getDeclaredMethod(
        "lokki\$coOriginal",
        Int::class.java,
        Array<Any?>::class.java,
        Continuation::class.java,
    )
    if (!dispatcher.isSynthetic) return "Fail: lokki\$coOriginal is not synthetic"

    val wrapper = mock<Wrapper>()
    coEvery { wrapper.name() } coAnswers { coCallOriginal() }
    coEvery { wrapper.result() } coAnswers { coCallOriginal() }
    if (complete { listOf(wrapper.name(), wrapper.result()) }.getOrNull() != listOf(Name("n"), Result.success("r"))) {
        return "Fail value classes over references"
    }
    if (!wrapper.javaClass.hasNested("lokki\$coOriginal\$1")) return "Fail: boxing a value class needs a continuation"

    val greeter = mock<Greeter>()
    coEvery { greeter.name() } returns "ann"
    coEvery { greeter.greet() } coAnswers { coCallOriginal() }
    if (complete { greeter.greet() }.getOrNull() != "hi ann") return "Fail default method"

    val base = mock<Base>()
    coEvery { base.name() } returns "bob"
    coEvery { base.greet() } coAnswers { coCallOriginal() }
    if (complete { base.greet() }.getOrNull() != "hi bob") return "Fail default method through a class"

    val polite = mock<Polite>()
    coEvery { polite.name() } returns "cid"
    coEvery { polite.greet() } answers { callOriginal() }
    val polited = complete { polite.greet() }.problem()
    if (polited != "callOriginal() of suspend Polite.greet() suspended; use coCallOriginal()") {
        return "Fail default method of a superinterface: $polited"
    }
    waiting.clear()

    val pump = mock<Pump>()
    coEvery { pump.pressure() } coAnswers { original() }
    if (complete { pump.pressure() }.problem() != "no original implementation for Pump.pressure()") {
        return "Fail abstract member"
    }
    coEvery { pump.pressure() } answers { plainOriginal() }
    if (complete { pump.pressure() }.problem() != "no original implementation for Pump.pressure()") {
        return "Fail abstract member through callOriginal"
    }

    return "OK"
}
