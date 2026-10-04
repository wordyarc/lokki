package sample

import io.lokki.Answer
import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.andThenJust
import io.lokki.every
import io.lokki.just
import io.lokki.mock

interface Counter {
    var step: Int
    fun next(): Int
    fun add(amount: Int, label: String): String
    fun reset()
}

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: Exception) {
        "${e::class.simpleName}: ${e.message}"
    }

fun box(): String {
    val counter = mock<Counter>()

    every { counter.next() } returns 1 andThen 2 andThenMany listOf(3, 4)
    if (List(5) { counter.next() } != listOf(1, 2, 3, 4, 4)) return "Fail: returns and andThen"

    every { counter.next() }.returnsMany(5, 6).andThenMany(7)
    if (List(4) { counter.next() } != listOf(5, 6, 7, 7)) return "Fail: returnsMany of vararg"

    every { counter.next() } returnsMany listOf(8) andThenThrows IllegalStateException("exhausted")
    if (counter.next() != 8) return "Fail: returnsMany of a list"
    if (failure { counter.next() } != "IllegalStateException: exhausted") return "Fail: andThenThrows"
    if (failure { counter.next() } != "IllegalStateException: exhausted") return "Fail: the last answer repeats"

    val error = IllegalArgumentException("bad amount")
    every { counter.add(less(0), any()) } throws error
    if (failure { counter.add(-1, "a") } != "IllegalArgumentException: bad amount") return "Fail: throws"

    every { counter.add(0, any()) } throwsMany listOf(error, IllegalStateException("second")) andThen "recovered"
    val thrown = List(2) { failure { counter.add(0, "a") } }
    if (thrown != listOf("IllegalArgumentException: bad amount", "IllegalStateException: second")) return "Fail: throwsMany"
    if (counter.add(0, "a") != "recovered") return "Fail: andThen after throwsMany"

    every { counter.add(more(0), any()) } answers { call ->
        if (call.invocation.self !== counter || self !== counter) return@answers "another self"
        if (invocation.args != listOf<Any?>(firstArg<Int>(), secondArg<String>())) return@answers "another args"
        "${arg<Int>(0) * 2}${lastArg<String>()}$nArgs"
    }
    if (counter.add(4, "x") != "8x2") return "Fail: answers ${counter.add(4, "x")}"

    every { counter.add(100, any()) } returnsArgument 1
    if (counter.add(100, "label") != "label") return "Fail: returnsArgument"
    val outside = 2
    val range = failure { every { counter.add(100, any()) } returnsArgument outside }
    if (range != "LokkiException: returnsArgument(2) is out of range: the number of arguments of Counter.add is 2") {
        return "Fail: returnsArgument out of range: $range"
    }

    var calls = 0
    every { counter.add(200, any()) } answers Answer { call -> "answer ${call.invocation.args[1]} ${++calls}" }
    if (counter.add(200, "a") != "answer a 1" || counter.add(200, "b") != "answer b 2") return "Fail: Answer"

    every { counter.add(300, any()) } returns "first" andThenAnswer { "then ${firstArg<Int>()}" } andThenAnswer
        Answer { "last" }
    if (List(3) { counter.add(300, "a") } != listOf("first", "then 300", "last")) return "Fail: andThenAnswer"

    every { counter.reset() } just Runs andThenThrows IllegalStateException("reset twice")
    counter.reset()
    if (failure { counter.reset() } != "IllegalStateException: reset twice") return "Fail: just Runs"

    every { counter.step = any() } throws IllegalStateException("read only") andThenJust Runs
    if (failure { counter.step = 1 } != "IllegalStateException: read only") return "Fail: throws of a setter"
    counter.step = 2

    every { counter.step } answers { nothing ?: 7 }
    if (counter.step != 7) return "Fail: nothing"

    val other = mock<Counter>()
    val stub = every { other.add(400, any()) }
    if (!failure { other.add(400, "a") }.startsWith("LokkiException: no answer found for Counter(")) {
        return "Fail: stub is published before the first answer"
    }
    stub returns "published"
    if (other.add(400, "a") != "published") return "Fail: stub is not published by the first answer"

    return "OK"
}
