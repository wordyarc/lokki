// MODULE: main
// MOCKABLE: sample.**
// FILE: clock.kt
package sample

interface TimeSource {
    fun now(): Long
}

object Clock : TimeSource {
    var zone: String = "UTC"

    val shownZone: String
        get() = zone

    override fun now(): Long = 1

    fun twice(): Long = now() * 2

    fun <T> echo(value: T): T = value

    suspend fun later(): Long = now() + 1

    fun format(prefix: String = "t"): String = "$prefix${now()}"

    internal fun secret(): Int = 7

    fun String.shout(): String = uppercase()

    tailrec fun countdown(n: Int): Int = if (n == 0) 0 else countdown(n - 1)

    fun log(vararg lines: String): Int = lines.size
}

class Settings {
    fun read(): String = mode

    companion object {
        var mode: String = "eco"

        @JvmStatic
        fun create(): String = "created"
    }
}

object Empty

interface Store<T> {
    fun put(value: T): Int
}

object Shelf : Store<String> {
    override fun put(value: String): Int = value.length
}

object Sizes {
    @JvmName("sizeOfInts")
    fun size(xs: List<Int>): Int = xs.size

    @JvmName("sizeOfStrings")
    fun size(xs: List<String>): Int = xs.size

    val List<Int>.first: Int
        @JvmName("firstOfInts") get() = get(0)

    val List<String>.first: Int
        @JvmName("firstOfStrings") get() = get(0).length
}

fun service(): String = "${Clock.now()}-${Settings.create()}"

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.LokkiException
import io.lokki.clearMocks
import io.lokki.coEvery
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.isMock
import io.lokki.lokkiScope
import io.lokki.mockObject
import io.lokki.unmockAll
import io.lokki.unmockObject
import io.lokki.verify
import io.lokki.Called
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import java.lang.reflect.Modifier
import kotlin.coroutines.startCoroutine
import sample.Clock
import sample.Empty
import sample.Settings
import sample.Shelf
import sample.Sizes
import sample.service

fun <T> complete(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    shape()?.let { return it }
    failure { mockObject(Clock) }.let {
        if (!it.startsWith("no active LokkiScope for mockObject()")) return "Fail: mockObject without a scope: $it"
    }
    failure { every { Clock.now() } returns 2 }.let {
        if (it != "Clock is not mocked in the current scope; call mockObject(Clock)") return "Fail: every without a scope: $it"
    }
    lokkiScope { stubs() }?.let { return it }
    if (Clock.now() != 1L || isMock(Clock)) return "Fail: the scope did not remove the redirect"
    lokkiScope { scopes() }?.let { return it }
    lokkiScope { companion() }?.let { return it }
    lokkiScope { jvmNames() }?.let { return it }
    return lokkiScope { block() } ?: "OK"
}

private fun shape(): String? {
    val clock = Clock::class.java
    val field = clock.getDeclaredField("lokki\$redirect")
    if (!Modifier.isStatic(field.modifiers) || !Modifier.isVolatile(field.modifiers)) return "Fail: redirect field"
    val companionField = Settings::class.java.getDeclaredField("lokki\$redirect")
    if (!Modifier.isStatic(companionField.modifiers)) return "Fail: redirect field of a companion"
    val generated = listOf(
        clock.getDeclaredMethod("getLokki\$redirect"),
        clock.getDeclaredMethod("now\$lokkiOriginal"),
        clock.getDeclaredMethod("zone\$lokkiGet"),
        clock.getDeclaredMethod("zone\$lokkiSet", String::class.java),
    )
    generated.find { !it.isSynthetic }?.let { return "Fail: ${it.name} is not synthetic" }
    val redirect = clock.declaredClasses.single { it.simpleName == "LokkiRedirect" }
    if (!redirect.isInterface || redirect.getMethod("now").isDefault.not()) return "Fail: redirect interface"
    return null
}

private fun stubs(): String? {
    mockObject(Clock)
    if (!isMock(Clock)) return "Fail: isMock"
    if (Clock.now() != 1L) return "Fail: an unstubbed call runs the original"
    every { Clock.now() } returns 5
    if (Clock.now() != 5L) return "Fail: stub"
    if (Clock.twice() != 10L) return "Fail: a call between members goes through the hook"
    if (service() != "5-created") return "Fail: a call from main"
    verify(exactly = 4) { Clock.now() }

    every { Clock.format(any()) } returns "any"
    every { Clock.format() } returns "default"
    if (Clock.format("t") != "default" || Clock.format("x") != "any") return "Fail: omitted default"
    every { Clock.echo<String>(any()) } returns "echo"
    if (Clock.echo("x") != "echo" || Clock.echo(1) != 1) return "Fail: generic member"
    coEvery { Clock.later() } returns 9
    if (complete { Clock.later() } != 9L) return "Fail: suspend member"
    every { Clock.secret() } returns 8
    if (Clock.secret() != 8) return "Fail: internal member"
    with(Clock) {
        every { "a".shout() } returns "B"
        if ("a".shout() != "B" || "c".shout() != "C") return "Fail: member extension"
    }
    every { Clock.countdown(3) } returns 42
    if (Clock.countdown(3) != 42 || Clock.countdown(2) != 0) return "Fail: tailrec member"
    every { Clock.log(*anyVararg()) } returns 7
    if (Clock.log("a", "b") != 7) return "Fail: vararg member"

    every { Clock.zone } returns "CET"
    if (Clock.zone != "CET") return "Fail: property"
    if (Clock.shownZone != "CET") return "Fail: a read of the property inside the object"
    every { Clock.zone = any() } answers { callOriginal() }
    Clock.zone = "PST"
    verify { Clock.zone = "PST" }
    every { Clock.zone } answers { callOriginal() }
    if (Clock.zone != "PST") return "Fail: callOriginal of a setter"
    Clock.zone = "UTC"

    clearMocks(Clock)
    if (Clock.now() != 1L) return "Fail: clearMocks of an object"
    verify { Clock.now() }
    confirmVerified(Clock)

    mockObject(Clock)
    verify(exactly = 0) { Clock.now() }
    verify { Clock wasNot Called }

    unmockObject(Clock)
    if (isMock(Clock)) return "Fail: unmockObject"
    failure { verify { Clock.now() } }.let {
        if (it != "Clock is not mocked in the current scope; call mockObject(Clock)") return "Fail: not mocked: $it"
    }
    every { Clock.now() } returns 2
    if (!isMock(Clock) || Clock.now() != 2L || Clock.twice() != 4L) return "Fail: every mocks an object that is not mocked"
    unmockObject(Clock)
    mockObject(Shelf)
    every { Shelf.put("a") } returns 9
    if (Shelf.put("a") != 9 || Shelf.put("bb") != 2) return "Fail: an override with another root key"

    mockObject(Clock, Empty)
    if (!isMock(Empty)) return "Fail: an object without members"
    unmockAll()
    if (isMock(Clock) || isMock(Empty)) return "Fail: unmockAll"
    return null
}

private fun scopes(): String? {
    mockObject(Clock)
    every { Clock.now() } returns 5
    lokkiScope {
        mockObject(Clock)
        every { Clock.twice() } returns 3
        unmockObject(Clock)
        if (Clock.now() != 5L || Clock.twice() != 3L) "Fail: a nested scope uses the redirect of its parent" else null
    }?.let { return it }
    if (Clock.now() != 5L || Clock.twice() != 10L) return "Fail: the nested scope rolled back its stubs only"
    return null
}

private fun companion(): String? {
    mockObject(Settings)
    every { Settings.mode } returns "sport"
    if (Settings().read() != "sport") return "Fail: a read of a companion property inside its class"
    every { Settings.create() } returns "mocked"
    if (Settings.create() != "mocked" || service() != "1-mocked") return "Fail: companion member"
    return null
}

private fun jvmNames(): String? {
    val sizes = Sizes::class.java
    sizes.getDeclaredMethod("sizeOfInts\$lokkiOriginal", List::class.java)
    sizes.getDeclaredMethod("firstOfStrings\$lokkiGet", List::class.java)
    mockObject(Sizes)
    every { Sizes.size(listOf(1)) } returns 9
    every { Sizes.size(any<List<String>>()) } answers { callOriginal() + 10 }
    if (Sizes.size(listOf(1)) != 9 || Sizes.size(listOf("a", "b")) != 12) return "Fail: overloads with @JvmName"
    with(Sizes) {
        every { listOf("abc").first } returns 7
        if (listOf("abc").first != 7 || listOf(5).first != 5) return "Fail: accessors with @JvmName"
    }
    verify { Sizes.size(listOf(1)) }
    return null
}

private fun block(): String? {
    mockObject(Clock) {
        every { Clock.now() } returns 4
        if (Clock.now() != 4L) return "Fail: block form"
    }
    if (Clock.now() != 1L) return "Fail: block form keeps the redirect"
    return null
}
