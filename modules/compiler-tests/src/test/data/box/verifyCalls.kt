package sample

import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify

interface Repo {
    fun find(id: Int, includeDeleted: Boolean = false): String
    fun save(name: String)
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
    val name = "$repo"
    every { repo.find(any(), any()) } returns "found"
    every { repo.save(any()) } just Runs

    verify(exactly = 0) { repo.find(any(), any()) }
    repo.find(1)
    repo.find(2, includeDeleted = true)
    verify { repo.find(1) }
    verify { repo.find(2, true) }
    verify(exactly = 2) { repo.find(any(), any()) }
    verify(atLeast = 1, atMost = 1) { repo.find(any()) }
    verify(inverse = true) { repo.find(3) }
    verify(inverse = true) {
        repo.find(1)
        repo.find(3)
    }
    failure(
        "Verification failed: call 1 of 1: $name.find(eq(2), eq(false)). No matching calls found.\n\n" +
            "Calls to same method:\n" +
            "1) $name.find(1, false)\n" +
            "   [0]: argument: 1, matcher: eq(2), result: -\n" +
            "   [1]: argument: false, matcher: eq(false), result: +\n" +
            "2) $name.find(2, true)\n" +
            "   [0]: argument: 2, matcher: eq(2), result: +\n" +
            "   [1]: argument: true, matcher: eq(false), result: -\n\n$CALL_SITES"
    ) { verify { repo.find(2) } }?.let { return it }
    failure(
        "Verification failed: call 1 of 1: $name.find(any(), any()). " +
            "2 matching calls found, but needs exactly 3 calls\n" +
            "Calls:\n1) $name.find(1, false)\n2) $name.find(2, true)\n\n$CALL_SITES"
    ) { verify(exactly = 3) { repo.find(any(), any()) } }?.let { return it }
    failure(
        "Verification failed: call 2 of 2: $name.count() was not called.\n\n" +
            "Calls to same mock:\n1) $name.find(1, false)\n2) $name.find(2, true)\n\n$CALL_SITES"
    ) {
        verify {
            repo.find(1)
            repo.count()
        }
    }?.let { return it }
    failure("Inverse verification failed.\n\nVerified calls:\n1) $name.find(1, false)") {
        verify(inverse = true) { repo.find(1) }
    }?.let { return it }
    val calls = 2
    val count = try {
        verify(atLeast = calls, exactly = calls) { repo.find(1) }
        null
    } catch (e: LokkiException) {
        e.message
    }
    if (count != "specify either atLeast/atMost or exactly") return "Fail: counts $count"

    val counter = mock<Repo>()
    every { counter.count() } returns 3
    every { counter.find(counter.count()) } returns "three"
    if (counter.find(3) != "three") return "Fail: stub with a call of a mock in the argument"
    verify { counter.find(counter.count()) }
    verify(exactly = 0) { counter.count() }
    verify(exactly = counter.count() - 2) { counter.find(3) }
    verify(exactly = 1) { counter.count() }

    val strict = mock<Repo>()
    val strictName = strict.toString()
    strict.hashCode()
    strict.equals(repo)
    failure("Verification failed: call 1 of 1: $strictName.count() was not called") {
        verify { strict.count() }
    }?.let { return it }
    if (runCatching { strict.count() }.exceptionOrNull() !is LokkiException) return "Fail: strict mock answered"
    verify { strict.count() }

    val names = mutableListOf<String>()
    repo.save("a")
    repo.save("b")
    verify(exactly = 2) { repo.save(capture(names)) }
    if (names != listOf("a", "b")) return "Fail: capture into a list $names"
    failure(
        "Verification failed: call 2 of 2: $name.find(eq(5), eq(false)). No matching calls found.\n\n" +
            "Calls to same method:\n" +
            "1) $name.find(1, false)\n" +
            "   [0]: argument: 1, matcher: eq(5), result: -\n" +
            "   [1]: argument: false, matcher: eq(false), result: +\n" +
            "2) $name.find(2, true)\n" +
            "   [0]: argument: 2, matcher: eq(5), result: -\n" +
            "   [1]: argument: true, matcher: eq(false), result: -\n\n$CALL_SITES"
    ) {
        verify {
            repo.save(capture(names))
            repo.find(5)
        }
    }?.let { return it }
    if (names != listOf("a", "b")) return "Fail: capture of a failed verification $names"
    val id = slot<Int>()
    verify { repo.find(capture(id), true) }
    if (id.captured != 2) return "Fail: capture into a slot"
    val saved = slot<String>()
    val twice = runCatching { verify { repo.save(capture(saved)) } }.exceptionOrNull()
    if (twice !is LokkiException || !twice.message!!.startsWith("$name.save(slotCapture()) execution is being verified more than once")) {
        return "Fail: slot captured twice $twice"
    }

    return "OK"
}
