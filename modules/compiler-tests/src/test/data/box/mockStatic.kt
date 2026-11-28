// MODULE: main
// INTERCEPTABLE: sample.** marked.Marked named.picked
// FILE: cars.kt
package sample

fun buildCar(color: String = "red"): String = "car:$color"

fun twice(): String = buildCar() + "," + buildCar("blue")

infix fun Int.op(b: Int): Int = this + b

val Int.selfOp: Int
    get() = this * this

var mode: String = "eco"

fun shownMode(): String = mode

fun <T> echo(value: T): T = value

suspend fun later(): Int = 1

fun log(vararg lines: String): Int = lines.size

internal fun secret(): Int = 7

tailrec fun countdown(n: Int): Int = if (n == 0) 0 else countdown(n - 1)

@JvmName("sizeOfInts")
fun size(xs: List<Int>): Int = xs.size

@JvmName("sizeOfStrings")
fun size(xs: List<String>): Int = xs.size

// FILE: other.kt
package sample

fun other(): String = "other"

// FILE: shared.kt
@file:JvmMultifileClass
@file:JvmName("Utils")

package sample

fun shared(): String = "shared"

// FILE: marked.kt
package marked

@Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION)
annotation class Marked

@Marked
fun annotated(): Int = 1

fun notAnnotated(): Int = 2

// FILE: markedFile.kt
@file:Marked

package marked

fun inMarkedFile(): Int = 3

// FILE: named.kt
package named

fun picked(): Int = 4

fun notPicked(): Int = 5

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.LokkiException
import io.lokki.clearAllMocks
import io.lokki.clearStaticMock
import io.lokki.coEvery
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mockStatic
import io.lokki.unmockAll
import io.lokki.unmockStatic
import io.lokki.verify
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import marked.annotated
import marked.inMarkedFile
import named.picked
import sample.buildCar
import sample.countdown
import sample.echo
import sample.later
import sample.log
import sample.mode
import sample.op
import sample.other
import sample.secret
import sample.selfOp
import sample.shared
import sample.shownMode
import sample.twice

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
        e.message!!
    }

fun box(): String {
    shape()?.let { return it }
    failure { mockStatic(::buildCar) }.let {
        if (!it.startsWith("no active LokkiScope for mockStatic()")) return "Fail: mockStatic without a scope: $it"
    }
    failure { every { buildCar() } returns "a" }.let {
        if (it != "::buildCar is not mocked and there is no active LokkiScope: use LokkiExtension or lokkiScope { }") {
            return "Fail: every without a scope: $it"
        }
    }
    lokkiScope { stubs() }?.let { return it }
    if (buildCar() != "car:red") return "Fail: the scope did not remove the redirect"
    lokkiScope { references() }?.let { return it }
    lokkiScope { scopes() }?.let { return it }
    return lokkiScope { block() } ?: "OK"
}

private fun shape(): String? {
    val facade = Class.forName("sample.CarsKt")
    val field = facade.declaredFields.single { it.name.startsWith("buildCar\$lokkiRedirect\$") }
    if (!Modifier.isStatic(field.modifiers) || !Modifier.isVolatile(field.modifiers)) return "Fail: redirect field"
    val original = facade.getDeclaredMethod("buildCar\$lokkiOriginal", String::class.java)
    if (!original.isSynthetic) return "Fail: the original is not synthetic"
    facade.getDeclaredMethod("sizeOfInts\$lokkiOriginal", List::class.java)
    val redirect = Class.forName("sample.CarsKt\$LokkiRedirect")
    if (!redirect.isInterface || !redirect.getMethod("buildCar", String::class.java).isDefault) {
        return "Fail: redirect interface"
    }
    Class.forName("sample.OtherKt\$LokkiRedirect")
    Class.forName("sample.Utils__SharedKt\$LokkiRedirect")
    val intercepted = listOf("marked.MarkedKt", "marked.MarkedFileKt", "named.NamedKt").flatMap { name ->
        Class.forName(name).declaredFields.map { it.name.substringBefore("\$lokkiRedirect\$", "") }
    }
    if (intercepted.filter { it.isNotEmpty() }.toSet() != setOf("annotated", "inMarkedFile", "picked")) {
        return "Fail: mockable patterns of top-level functions: $intercepted"
    }
    return null
}

private fun stubs(): String? {
    mockStatic(::buildCar)
    if (buildCar() != "car:red") return "Fail: an unstubbed call runs the original"
    every { buildCar(any()) } returns "mocked"
    if (buildCar("x") != "mocked") return "Fail: stub"
    if (twice() != "mocked,mocked") return "Fail: a call from main"
    verify(exactly = 4) { buildCar(any()) }
    every { buildCar() } returns "default"
    if (buildCar("red") != "default" || buildCar("x") != "mocked") return "Fail: omitted default"
    if (other() != "other") return "Fail: an unmocked function of another file"

    mockStatic(Int::op)
    mockStatic(Int::selfOp)
    every { 5 op 6 } returns 2
    every { any<Int>() op 1 } returns 9
    if (5 op 6 != 2 || 3 op 1 != 9 || 2 op 2 != 4) return "Fail: extension function"
    every { 5.selfOp } returns 3
    if (5.selfOp != 3 || 4.selfOp != 16) return "Fail: extension property"

    mockStatic(::mode)
    every { mode } returns "sport"
    if (mode != "sport" || shownMode() != "sport") return "Fail: top-level property"
    every { mode = any() } answers { callOriginal() }
    mode = "city"
    verify { mode = "city" }
    every { mode } answers { callOriginal() }
    if (mode != "city") return "Fail: callOriginal of a setter"

    if (echo("x") != "x") return "Fail: a generic function, which cannot be referenced, runs the original"
    mockStatic(::later, ::log, ::secret, ::countdown)
    coEvery { later() } returns 9
    if (complete { later() } != 9) return "Fail: suspend function"
    every { log(*anyVararg()) } returns 7
    if (log("a", "b") != 7) return "Fail: vararg function"
    every { secret() } returns 8
    if (secret() != 8) return "Fail: internal function"
    every { countdown(3) } returns 42
    if (countdown(3) != 42 || countdown(2) != 0) return "Fail: tailrec function"

    mockStatic(::shared)
    every { shared() } returns "mocked"
    if (shared() != "mocked") return "Fail: a function of a multifile class"
    mockStatic(::annotated, ::inMarkedFile, ::picked)
    every { annotated() } returns 10
    every { inMarkedFile() } returns 30
    every { picked() } returns 40
    if (annotated() != 10 || inMarkedFile() != 30 || picked() != 40) return "Fail: annotated and named functions"

    unmockStatic(::buildCar)
    if (buildCar("x") != "car:x") return "Fail: unmockStatic"
    failure { verify { buildCar() } }.let {
        if (it != "::buildCar is not mocked in the current scope; call mockStatic(::buildCar)") return "Fail: $it"
    }
    every { buildCar() } returns "a"
    if (buildCar() != "a" || buildCar("x") != "car:x") return "Fail: every mocks a function that is not mocked"
    verify { buildCar() }
    unmockAll()
    if (5 op 6 != 11) return "Fail: unmockAll"
    return null
}

private fun references(): String? {
    mockStatic(Int::op)
    mockStatic(Int::selfOp)
    every { 5 op 6 } returns 2
    every { 5.selfOp } returns 99
    if (5 op 6 != 2 || 5.selfOp != 99) return "Fail: stubs"
    verify(exactly = 1) { 5 op 6 }
    confirmVerified(Int::op, clear = true)
    verify(exactly = 0) { 5 op 6 }
    if (runCatching { confirmVerified(Int::selfOp) }.exceptionOrNull() !is AssertionError) {
        return "Fail: confirmVerified of a property"
    }
    verify { 5.selfOp }
    confirmVerified(Int::selfOp)

    clearStaticMock(Int::op)
    if (5 op 6 != 11) return "Fail: clearStaticMock"
    every { 5 op 6 } returns 2
    clearAllMocks(staticMocks = false)
    if (5 op 6 != 2) return "Fail: clearAllMocks without static mocks"
    clearAllMocks(regularMocks = false, objectMocks = false)
    if (5 op 6 != 11) return "Fail: clearAllMocks of static mocks"
    return null
}

private fun scopes(): String? {
    mockStatic(::buildCar)
    every { buildCar(any()) } returns "outer"
    lokkiScope {
        mockStatic(::buildCar)
        every { buildCar("inner") } returns "inner"
        unmockStatic(::buildCar)
        if (buildCar("x") != "outer" || buildCar("inner") != "inner") "Fail: a nested scope" else null
    }?.let { return it }
    if (buildCar("inner") != "outer") return "Fail: the nested scope rolled back its stubs only"
    return null
}

private fun block(): String? {
    mockStatic(::buildCar) {
        every { buildCar(any()) } returns "block"
        if (buildCar() != "block") return "Fail: block form"
    }
    if (buildCar() != "car:red") return "Fail: block form keeps the redirect"
    return null
}
