package sample

import io.lokki.LokkiException
import io.lokki.MatcherScope
import io.lokki.Runs
import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

interface Repo {
    fun find(id: Int): String
    suspend fun load(id: Int): String
    fun name(name: String?): String
    fun save(value: Any)
}

suspend fun isEven(value: Int): Boolean = value % 2 == 0

suspend fun never(): Boolean = suspendCoroutine { }

fun MatcherScope.even(): Int = coMatch { isEven(it) }

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
    } catch (e: AssertionError) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    val repo = mock<Repo>()

    every { repo.find(coMatch { isEven(it) }) } returns "even"
    every { repo.find(coMatch { !isEven(it) }) } returns "odd"
    if (repo.find(2) + repo.find(3) != "evenodd") return "Fail coMatch"

    coEvery { repo.load(even()) } returns "loaded"
    if (complete { repo.load(4) } != "loaded") return "Fail suspend member"
    val oddLoad = failure { complete { repo.load(5) } }
    if (!oddLoad.startsWith("no answer found for Repo.load(5)") || "Repo.load(matcher())" !in oddLoad) {
        return "Fail suspend member strict: $oddLoad"
    }

    every { repo.name(coMatchNullable { it == null }) } returns "null"
    every { repo.name(coMatch { it.isEmpty() }) } returns "empty"
    if (repo.name(null) + repo.name("") != "nullempty") return "Fail coMatchNullable"

    every { repo.save(coMatch<String> { it.length > 1 }) } just Runs
    repo.save("ab")
    val typed = failure { repo.save(10) }
    if (!typed.startsWith("no answer found for Repo.save(10)")) return "Fail typed: $typed"

    verify { repo.find(coWithArg { if (!isEven(it)) throw AssertionError("odd $it") }) }
    coVerify { repo.load(coWithArg { if (!isEven(it)) throw AssertionError("odd $it") }) }
    verify { repo.name(coWithNullableArg { if (it != null) throw AssertionError("not null") }) }
    val notFour = failure { verify { repo.find(coWithArg { if (it != 4) throw AssertionError("not 4: $it") }) } }
    if ("Assertion errors in matchers:" !in notFour || "not 4: 2" !in notFour || "not 4: 3" !in notFour) {
        return "Fail coWithArg report: $notFour"
    }

    val suspended = mock<Repo>()
    every { suspended.find(coMatch { never() }) } returns "never"
    val suspension = failure { suspended.find(1) }
    if (suspension != "coMatch suspended; a matcher must complete without suspension") {
        return "Fail suspension: $suspension"
    }
    every { suspended.save(any()) } just Runs
    suspended.save(1)
    val verifySuspension = failure { verify { suspended.save(coWithArg<Int> { never() }) } }
    if (verifySuspension != "coWithArg suspended; a matcher must complete without suspension") {
        return "Fail verify suspension: $verifySuspension"
    }

    return "OK"
}
