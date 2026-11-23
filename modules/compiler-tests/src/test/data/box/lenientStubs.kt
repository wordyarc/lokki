package sample

import io.lokki.checkUnnecessaryStub
import io.lokki.every
import io.lokki.lenient
import io.lokki.mock

interface Repo {
    fun find(id: Int): String

    fun drop(id: Int)

    suspend fun load(id: Int): String

    suspend fun clear()
}

fun box(): String {
    val repo = mock<Repo>()
    val value = lenient {
        every { repo.find(1) } returns "one"
        coEvery { repo.load(1) } returns "loaded"
        justRun { repo.drop(1) }
        coJustRun { repo.clear() }
        42
    }
    if (value != 42) return "Fail result of lenient"
    checkUnnecessaryStub(repo)
    if (repo.find(1) != "one") return "Fail lenient answer"

    every { repo.find(2) } returns "two"
    try {
        checkUnnecessaryStub(repo)
    } catch (e: AssertionError) {
        val message = e.message.orEmpty()
        if ("find(eq(2))" !in message) return "Fail: $message"
        if ("drop" in message || "load" in message) return "Fail lenient stub reported: $message"
        return "OK"
    }
    return "Fail regular stub not reported"
}
