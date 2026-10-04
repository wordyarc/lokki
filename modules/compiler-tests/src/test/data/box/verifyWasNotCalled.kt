package sample

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mock
import io.lokki.verify

interface Repo {
    fun find(id: Int): String
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
    val other = mock<Repo>("other")
    val name = "$repo"
    val otherName = "$other"
    every { repo.find(any()) } returns "found"

    verify { repo wasNot Called }
    verify { listOf(repo, other) wasNot Called }
    repo.hashCode()
    repo.equals(other)
    verify { return@verify repo wasNot Called }

    repo.find(1)
    failure("Verification failed: $name should not be called:\n1) $name.find(1)\n\n$CALL_SITES") {
        verify { repo wasNot Called }
    }?.let { return it }
    failure("Verification failed: $name should not be called:\n1) $name.find(1)\n\n$CALL_SITES") {
        verify { listOf(other, repo, repo) wasNot Called }
    }?.let { return it }
    verify {
        repo.find(1)
        other wasNot Called
    }
    confirmVerified(repo)
    failure("Verification failed: call 1 of 1: $otherName.count() was not called") {
        verify {
            repo wasNot Called
            other.count()
        }
    }?.let { return it }

    verify(inverse = true) { repo wasNot Called }
    verify(inverse = true) {
        repo.find(1)
        repo wasNot Called
    }
    failure("Inverse verification failed.\n\nVerified calls:\n") {
        verify(inverse = true) { other wasNot Called }
    }?.let { return it }

    if (runCatching { other.count() }.exceptionOrNull() !is LokkiException) return "Fail: strict mock answered"
    failure(
        "Calls without an answer threw LokkiException:\n1) $otherName.count()\n\n" +
            "Verification failed: $name, $otherName should not be called:\n" +
            "1) $name.find(1)\n2) $otherName.count()\n\n$CALL_SITES"
    ) { verify { listOf(repo, other) wasNot Called } }?.let { return it }

    val notMock = runCatching { verify { "text" wasNot Called } }.exceptionOrNull()
    if (notMock !is LokkiException || notMock.message != "text is not a lokki mock") return "Fail: not a mock $notMock"

    val quiet = mock<Repo>()
    var evaluations = 0
    fun target(): Repo {
        evaluations++
        return quiet
    }
    verify { target() wasNot Called }
    if (evaluations != 1) return "Fail: receiver evaluated $evaluations times"

    return "OK"
}
