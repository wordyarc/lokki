package sample

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mock
import io.lokki.verifyCount

interface Repo {
    fun find(id: Int, includeDeleted: Boolean = false): String
    fun count(): Int
}

private const val CALL_SITES = "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true"

private fun failure(expected: String, block: () -> Unit): String? {
    val message = try {
        block()
        null
    } catch (e: AssertionError) {
        e.message
    }
    return if (message == expected) null else "Fail: expected <$expected>, but was <$message>"
}

fun box(): String {
    val repo = mock<Repo>()
    val other = mock<Repo>()
    val name = "$repo"
    every { repo.find(any(), any()) } returns "found"

    repo.find(1)
    repo.find(1, includeDeleted = true)
    repo.find(2)

    verifyCount {
        2 * { repo.find(1, any()) }
        1 * { repo.find(2) }
        0 * { repo.find(3) }
        (1..3) * { repo.find(1, true) }
        (0..1) * { repo.find(2) }
        (0..0) * { repo.find(3) }
        (0..Int.MAX_VALUE) * { repo.count() }
        1 * { other wasNot Called }
    }
    confirmVerified(repo)

    failure(
        "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
            "2 matching calls found, but needs exactly 3 calls\n" +
            "Calls:\n1) $name.find(1, false)\n2) $name.find(1, true)\n3) $name.find(2, false)\n\n$CALL_SITES"
    ) { verifyCount { 3 * { repo.find(1, any()) } } }?.let { return it }
    failure(
        "Verification failed: call 1 of 1: $name.find(eq(2), eq(false)). " +
            "1 matching calls found, but needs at least 2 and at most 4 calls\n" +
            "Calls:\n1) $name.find(1, false)\n2) $name.find(1, true)\n3) $name.find(2, false)\n\n$CALL_SITES"
    ) { verifyCount { (2..4) * { repo.find(2) } } }?.let { return it }
    failure(
        "Verification failed: call 1 of 1: $name.find(eq(1), any()) should not be called\n\n" +
            "Calls:\n1) $name.find(1, false)\n2) $name.find(1, true)\n3) $name.find(2, false)\n\n$CALL_SITES"
    ) { verifyCount { 0 * { repo.find(1, any()) } } }?.let { return it }

    var evaluations = 0
    fun twice(): Int {
        evaluations++
        return 2
    }
    verifyCount {
        twice() * {
            repo.find(any(), false)
            repo.find(1, any())
        }
    }
    if (evaluations != 1) return "Fail: count evaluated $evaluations times"

    val calls = -1
    val negative = runCatching { verifyCount { calls * { repo.find(1) } } }.exceptionOrNull()
    if (negative !is LokkiException || negative.message != "atLeast should be positive") return "Fail: $negative"
    val empty = runCatching { verifyCount { (2..1) * { repo.find(1) } } }.exceptionOrNull()
    if (empty !is LokkiException || empty.message != "atLeast should less or equal atMost") return "Fail: $empty"

    return "OK"
}
