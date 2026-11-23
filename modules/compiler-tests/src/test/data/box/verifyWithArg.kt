package sample

import io.lokki.VerificationScope
import io.lokki.checkEquals
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import io.lokki.verifyCount

interface Shape

class Circle(val radius: Int) : Shape

class Square(val side: Int) : Shape

interface Repo {
    fun find(id: Int, name: String? = null): String
    fun draw(shape: Shape): String
    fun sum(vararg values: Int): Int
}

private const val CALL_SITES = "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true"

private fun positive(value: Int): Boolean {
    if (value <= 0) throw AssertionError("$value is not positive")
    return true
}

fun VerificationScope.positiveId(): Int = withArg { positive(it) }

private fun failure(block: () -> Unit): AssertionError? =
    try {
        block()
        null
    } catch (e: AssertionError) {
        e
    }

fun assertions(): String? {
    val repo = mock<Repo>()
    every { repo.find(any(), any()) } returns "found"
    every { repo.draw(any()) } returns "drawn"
    every { repo.sum(any(), any()) } returns 3

    repo.find(1, "a")
    repo.find(-2)
    repo.draw(Square(3))
    repo.sum(1, 2)

    var runs = 0
    verify {
        repo.find(
            withArg {
                runs++
                positive(it)
            },
            any(),
        )
    }
    if (runs != 2) return "the block ran $runs times"
    verify(exactly = 1) { repo.find(withArg { positive(it) }, any()) }
    verify { repo.find(withArg { checkEquals(1) }, withArg { checkEquals("a") }) }
    verify { repo.find(name = withNullableArg { checkEquals(null) }, id = -2) }
    verify { repo.find(positiveId(), any()) }
    verify { repo.find(and(withArg { positive(it) }, less(2)), any()) }
    verify { repo.find(not(withArg { positive(it) }), any()) }
    verify { repo.sum(1, withArg { positive(it) }) }
    verifyCount {
        1 * { repo.find(withArg { positive(it) }, any()) }
        2 * { repo.find(withArg { }, any()) }
    }

    var received = 0
    verify(inverse = true) { repo.find(-2, withArg<String> { received++ }) }
    verify(inverse = true) { repo.draw(withArg<Circle> { received++ }) }
    if (received != 0) return "withArg received a null or another type"
    verify { repo.draw(withArg<Square> { if (it.side != 3) throw AssertionError("side ${it.side}") }) }

    val broken = runCatching { verify { repo.find(withArg { error("broken") }, any()) } }.exceptionOrNull()
    if (broken !is IllegalStateException || broken.message != "broken") return "an exception of the block $broken"
    return null
}

fun failures(): String? {
    val repo = mock<Repo>()
    val name = "$repo"
    every { repo.find(any(), any()) } returns "found"
    repo.find(1, "a")
    repo.find(-2)

    val unequal = failure { verify { repo.find(withArg { checkEquals(5) }, any()) } }
        ?: return "withArg of unequal arguments passed"
    val (first, second) = unequal.suppressedExceptions
    if (first.message != "expected [5] but found [1]" || second.message != "expected [5] but found [-2]") {
        return "suppressed ${unequal.suppressedExceptions}"
    }
    val expected = "Verification failed: call 1 of 1: $name.find(matcher(), any()). No matching calls found.\n\n" +
        "Calls to same method:\n1) $name.find(1, a)\n2) $name.find(-2, null)\n\n$CALL_SITES\n\n" +
        "Assertion errors in matchers:\n" +
        "1) $name.find(1, a), argument [0]: java.lang.AssertionError: expected [5] but found [1]\n" +
        "2) $name.find(-2, null), argument [0]: java.lang.AssertionError: expected [5] but found [-2]"
    if (unequal.message != expected) return "report <${unequal.message}>"

    val predicate = failure { verify { repo.find(match { positive(it) }, null) } }
        ?: return "match of a failed assertion passed"
    if (!predicate.message!!.endsWith(
            "Assertion errors in matchers:\n" +
                "1) $name.find(-2, null), argument [0]: java.lang.AssertionError: -2 is not positive"
        )
    ) {
        return "report of match <${predicate.message}>"
    }
    return null
}

fun stubs(): String? {
    val repo = mock<Repo>()
    every { repo.find(any(), any()) } returns "found"
    every { repo.find(match { positive(it) }, "stub") } returns "positive"

    if (repo.find(3, "stub") != "positive") return "match of a passed assertion"
    if (repo.find(-3, "stub") != "found") return "match of a failed assertion"
    return null
}

fun box(): String {
    assertions()?.let { return "Fail: $it" }
    failures()?.let { return "Fail: $it" }
    stubs()?.let { return "Fail: $it" }
    return "OK"
}
