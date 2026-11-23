package sample

import io.lokki.CapturingSlot
import io.lokki.Matcher
import io.lokki.MatcherScope
import io.lokki.VerificationScope
import io.lokki.every
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify

interface Sink {
    fun take(value: Any?): String
}

class OneLetter : Matcher<String> {
    override fun match(arg: String?): Boolean = arg?.length == 1
}

fun MatcherScope.anyText(): String = any()

fun MatcherScope.anyNullableText(): String = anyNullable()

fun MatcherScope.equalTo(text: String): String = eq(text)

fun MatcherScope.notEqualTo(text: String): String = neq(text)

fun MatcherScope.same(text: String): String = refEq(text)

fun MatcherScope.notSame(text: String): String = nrefEq(text)

fun MatcherScope.absent(): String = isNull()

fun MatcherScope.present(): String = isNull(inverse = true)

fun MatcherScope.text(): String = ofType()

fun MatcherScope.five(): Int = cmpEq(5)

fun MatcherScope.moreThanFive(): Int = more(5)

fun MatcherScope.lessThanFive(): Int = less(5)

fun MatcherScope.upToFive(): Int = range(1, 5)

fun MatcherScope.between(): Int = and(more(1), less(9))

fun MatcherScope.edge(): Int = or(eq(1), eq(9))

fun MatcherScope.notOne(): Int = not(eq(1))

fun MatcherScope.letter(): String = match { it.length == 1 }

fun MatcherScope.letterOrNull(): String = matchNullable { it == null || it.length == 1 }

fun MatcherScope.oneLetter(): String = match(OneLetter())

fun MatcherScope.coLetter(): String = coMatch { it.length == 1 }

fun MatcherScope.coLetterOrNull(): String = coMatchNullable { it == null || it.length == 1 }

fun MatcherScope.captured(list: MutableList<String>): String = capture(list)

fun MatcherScope.captured(slot: CapturingSlot<String>): String = capture(slot)

fun MatcherScope.capturedNullable(list: MutableList<String?>): String? = captureNullable(list)

fun MatcherScope.capturedNullable(slot: CapturingSlot<String?>): String? = captureNullable(slot)

fun MatcherScope.lambda(): () -> Int = captureLambda()

fun MatcherScope.coroutine(): suspend () -> Int = captureCoroutine()

fun MatcherScope.pair(): Array<Any?> = array(eq(1), eq(2))

fun VerificationScope.textArgument(): String = withArg { }

fun VerificationScope.nullableTextArgument(): String = withNullableArg { }

fun VerificationScope.coTextArgument(): String = coWithArg { }

fun VerificationScope.coNullableTextArgument(): String = coWithNullableArg { }

private val long = "long"
private val function: () -> Int = { 1 }
private val suspending: suspend () -> Int = { 1 }
private val values =
    listOf(null, 1, 5, 9, "a", long, 2.5, function, suspending, arrayOf<Any?>(1, "a"), arrayOf<Any?>(1, 2), intArrayOf(1, 2))

private fun outcome(block: () -> String): String =
    try {
        block()
    } catch (e: Throwable) {
        e::class.simpleName!!
    }

private fun stubbed(state: () -> Any? = { null }, stub: (Sink) -> Unit): List<String> {
    val sink = mock<Sink>()
    stub(sink)
    return values.map { value -> outcome { sink.take(value) } } + state().toString()
}

private fun verified(check: (Sink) -> Unit): List<String> =
    values.map { value ->
        val sink = mock<Sink>(relaxed = true)
        sink.take(value)
        outcome {
            check(sink)
            "verified"
        }
    }

private fun stubs(): List<Triple<String, List<String>, List<String>>> {
    val directList = mutableListOf<String>()
    val helperList = mutableListOf<String>()
    val directSlot = slot<String>()
    val helperSlot = slot<String>()
    val directNullableList = mutableListOf<String?>()
    val helperNullableList = mutableListOf<String?>()
    val directNullableSlot = slot<String?>()
    val helperNullableSlot = slot<String?>()
    return listOf(
        Triple(
            "any",
            stubbed { every { it.take(any<String>()) } returns "hit" },
            stubbed { every { it.take(anyText()) } returns "hit" },
        ),
        Triple(
            "anyNullable",
            stubbed { every { it.take(anyNullable<String>()) } returns "hit" },
            stubbed { every { it.take(anyNullableText()) } returns "hit" },
        ),
        Triple(
            "eq",
            stubbed { every { it.take(eq("a")) } returns "hit" },
            stubbed { every { it.take(equalTo("a")) } returns "hit" },
        ),
        Triple(
            "neq",
            stubbed { every { it.take(neq("a")) } returns "hit" },
            stubbed { every { it.take(notEqualTo("a")) } returns "hit" },
        ),
        Triple(
            "refEq",
            stubbed { every { it.take(refEq(long)) } returns "hit" },
            stubbed { every { it.take(same(long)) } returns "hit" },
        ),
        Triple(
            "nrefEq",
            stubbed { every { it.take(nrefEq(long)) } returns "hit" },
            stubbed { every { it.take(notSame(long)) } returns "hit" },
        ),
        Triple(
            "isNull",
            stubbed { every { it.take(isNull<String>()) } returns "hit" },
            stubbed { every { it.take(absent()) } returns "hit" },
        ),
        Triple(
            "isNull(inverse)",
            stubbed { every { it.take(isNull<String>(inverse = true)) } returns "hit" },
            stubbed { every { it.take(present()) } returns "hit" },
        ),
        Triple(
            "ofType",
            stubbed { every { it.take(ofType<String>()) } returns "hit" },
            stubbed { every { it.take(text()) } returns "hit" },
        ),
        Triple(
            "cmpEq",
            stubbed { every { it.take(cmpEq(5)) } returns "hit" },
            stubbed { every { it.take(five()) } returns "hit" },
        ),
        Triple(
            "more",
            stubbed { every { it.take(more(5)) } returns "hit" },
            stubbed { every { it.take(moreThanFive()) } returns "hit" },
        ),
        Triple(
            "less",
            stubbed { every { it.take(less(5)) } returns "hit" },
            stubbed { every { it.take(lessThanFive()) } returns "hit" },
        ),
        Triple(
            "range",
            stubbed { every { it.take(range(1, 5)) } returns "hit" },
            stubbed { every { it.take(upToFive()) } returns "hit" },
        ),
        Triple(
            "and",
            stubbed { every { it.take(and(more(1), less(9))) } returns "hit" },
            stubbed { every { it.take(between()) } returns "hit" },
        ),
        Triple(
            "or",
            stubbed { every { it.take(or(eq(1), eq(9))) } returns "hit" },
            stubbed { every { it.take(edge()) } returns "hit" },
        ),
        Triple(
            "not",
            stubbed { every { it.take(not(eq(1))) } returns "hit" },
            stubbed { every { it.take(notOne()) } returns "hit" },
        ),
        Triple(
            "match",
            stubbed { every { it.take(match<String> { it.length == 1 }) } returns "hit" },
            stubbed { every { it.take(letter()) } returns "hit" },
        ),
        Triple(
            "matchNullable",
            stubbed { every { it.take(matchNullable<String> { it == null || it.length == 1 }) } returns "hit" },
            stubbed { every { it.take(letterOrNull()) } returns "hit" },
        ),
        Triple(
            "match(Matcher)",
            stubbed { every { it.take(match(OneLetter())) } returns "hit" },
            stubbed { every { it.take(oneLetter()) } returns "hit" },
        ),
        Triple(
            "coMatch",
            stubbed { every { it.take(coMatch<String> { it.length == 1 }) } returns "hit" },
            stubbed { every { it.take(coLetter()) } returns "hit" },
        ),
        Triple(
            "coMatchNullable",
            stubbed { every { it.take(coMatchNullable<String> { it == null || it.length == 1 }) } returns "hit" },
            stubbed { every { it.take(coLetterOrNull()) } returns "hit" },
        ),
        Triple(
            "capture(list)",
            stubbed({ directList }) { every { it.take(capture(directList)) } returns "hit" },
            stubbed({ helperList }) { every { it.take(captured(helperList)) } returns "hit" },
        ),
        Triple(
            "capture(slot)",
            stubbed({ directSlot }) { every { it.take(capture(directSlot)) } returns "hit" },
            stubbed({ helperSlot }) { every { it.take(captured(helperSlot)) } returns "hit" },
        ),
        Triple(
            "captureNullable(list)",
            stubbed({ directNullableList }) { every { it.take(captureNullable(directNullableList)) } returns "hit" },
            stubbed({ helperNullableList }) { every { it.take(capturedNullable(helperNullableList)) } returns "hit" },
        ),
        Triple(
            "captureNullable(slot)",
            stubbed({ directNullableSlot }) { every { it.take(captureNullable(directNullableSlot)) } returns "hit" },
            stubbed({ helperNullableSlot }) { every { it.take(capturedNullable(helperNullableSlot)) } returns "hit" },
        ),
        Triple(
            "captureLambda",
            stubbed { every { it.take(captureLambda<() -> Int>()) } returns "hit" },
            stubbed { every { it.take(lambda()) } returns "hit" },
        ),
        Triple(
            "captureCoroutine",
            stubbed { every { it.take(captureCoroutine<suspend () -> Int>()) } returns "hit" },
            stubbed { every { it.take(coroutine()) } returns "hit" },
        ),
        Triple(
            "array",
            stubbed { every { it.take(array<Array<Any?>>(eq(1), eq(2))) } returns "hit" },
            stubbed { every { it.take(pair()) } returns "hit" },
        ),
    )
}

private fun verifications(): List<Triple<String, List<String>, List<String>>> =
    listOf(
        Triple(
            "withArg",
            verified { verify { it.take(withArg<String> { }) } },
            verified { verify { it.take(textArgument()) } },
        ),
        Triple(
            "withNullableArg",
            verified { verify { it.take(withNullableArg<String> { }) } },
            verified { verify { it.take(nullableTextArgument()) } },
        ),
        Triple(
            "coWithArg",
            verified { verify { it.take(coWithArg<String> { }) } },
            verified { verify { it.take(coTextArgument()) } },
        ),
        Triple(
            "coWithNullableArg",
            verified { verify { it.take(coWithNullableArg<String> { }) } },
            verified { verify { it.take(coNullableTextArgument()) } },
        ),
    )

fun box(): String {
    for ((matcher, direct, helper) in stubs() + verifications()) {
        if (direct != helper) return "Fail: $matcher matches $direct directly and $helper in a helper"
    }
    return "OK"
}
