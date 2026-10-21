package sample

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.Ordering
import io.lokki.Runs
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify
import io.lokki.verifyAll
import io.lokki.verifyOrder
import io.lokki.verifySequence

interface Repo {
    fun find(id: Int, includeDeleted: Boolean = false): String
    fun save(name: String)
    fun door(id: Int): Door
}

interface Door {
    fun open()
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
    repo.find(1)
    repo.save("a")
    repo.find(2)

    verifyOrder {
        repo.find(1)
        repo.find(2)
    }
    verifyOrder {
        repo.save(any())
        repo.find(2)
    }
    verifyOrder(inverse = true) {
        repo.find(2)
        repo.find(1)
    }
    verifySequence {
        repo.find(1)
        repo.save("a")
        repo.find(2)
    }
    verifySequence(inverse = true) {
        repo.find(1)
        repo.find(2)
    }
    verifyAll {
        repo.find(any())
        repo.save(any())
    }
    verifyAll(inverse = true) { repo.find(1) }
    verify(Ordering.ORDERED) {
        repo.find(1)
        repo.find(2)
    }
    val sequence = Ordering.SEQUENCE
    verify(sequence) {
        repo.find(1)
        repo.save("a")
        repo.find(2)
    }

    failure(
        "Verification failed: calls are not in verification order\n\nMatchers: \n" +
            "+$name.find(eq(2), eq(false))\n$name.find(eq(1), eq(false))\n\n" +
            "Calls:\n1) $name.find(1, false)\n2) $name.save(a)\n3) +$name.find(2, false)\n\n$CALL_SITES"
    ) {
        verifyOrder {
            repo.find(2)
            repo.find(1)
        }
    }?.let { return it }
    failure(
        "Verification failed: fewer calls happened than demanded by order verification sequence. \n\nMatchers: \n" +
            "+$name.find(eq(1), eq(false))\n$name.find(eq(3), eq(false))\n+$name.find(eq(2), eq(false))\n" +
            "$name.save(any())\n\n" +
            "Calls:\n1) +$name.find(1, false)\n2) $name.save(a)\n3) +$name.find(2, false)\n\n$CALL_SITES"
    ) {
        verifyOrder {
            repo.find(1)
            repo.find(3)
            repo.find(2)
            repo.save(any())
        }
    }?.let { return it }
    failure(
        "Verification failed: number of calls happened not matching exact number of verification sequence\n\n" +
            "Matchers: \n+$name.find(eq(1), eq(false))\n+$name.find(eq(2), eq(false))\n\n" +
            "Calls:\n1) +$name.find(1, false)\n2) $name.save(a)\n3) +$name.find(2, false)\n\n$CALL_SITES"
    ) {
        verifySequence {
            repo.find(1)
            repo.find(2)
        }
    }?.let { return it }
    failure(
        "Verification failed: calls are not exactly matching verification sequence\n\nMatchers: \n" +
            "+$name.find(eq(1), eq(false))\n+$name.find(eq(2), eq(false))\n$name.save(any())\n\n" +
            "Calls:\n1) +$name.find(1, false)\n2) $name.save(a)\n3) +$name.find(2, false)\n\n$CALL_SITES"
    ) {
        verifySequence {
            repo.find(1)
            repo.find(2)
            repo.save(any())
        }
    }?.let { return it }
    failure(
        "Verification failed: some calls were not matched: [$name.save(a)]\n\nMatchers: \n" +
            "+$name.find(any(), any())\n\n" +
            "Calls:\n1) $name.find(1, false)\n2) $name.save(a)\n3) +$name.find(2, false)\n\n$CALL_SITES"
    ) { verifyAll { repo.find(any(), any()) } }?.let { return it }
    failure(
        "Verification failed: call 2 of 2: $name.save(eq(b)). Only one matching call to $name/save happened, " +
            "but arguments are not matching:\n[0]: argument: a, matcher: eq(b), result: -\n\n$CALL_SITES"
    ) {
        verifyAll {
            repo.find(any(), any())
            repo.save("b")
        }
    }?.let { return it }
    failure("Inverse verification failed.\n\nVerified calls:\n1) $name.find(1, false)\n2) $name.find(2, false)") {
        verifyOrder(inverse = true) {
            repo.find(1)
            repo.find(2)
        }
    }?.let { return it }

    val ordered = Ordering.ORDERED
    val calls = 2
    val counts = try {
        verify(ordered, atLeast = calls) { repo.find(1) }
        null
    } catch (e: LokkiException) {
        e.message
    }
    if (counts != "atLeast, atMost, exactly is only allowed in unordered verify block") return "Fail: counts $counts"

    val ids = mutableListOf<Int>()
    verifyOrder {
        repo.find(capture(ids))
        repo.find(capture(ids))
    }
    if (ids != listOf(1, 2)) return "Fail: captured in order $ids"
    val saved = slot<String>()
    verifySequence {
        repo.find(1)
        repo.save(capture(saved))
        repo.find(2)
    }
    if (saved.captured != "a") return "Fail: captured in sequence ${saved.captured}"

    val acknowledged = mock<Repo>()
    every { acknowledged.find(any(), any()) } returns "found"
    acknowledged.find(1)
    acknowledged.find(2)
    acknowledged.find(1)
    verifyOrder {
        acknowledged.find(1)
        acknowledged.find(1)
    }
    val unverified = try {
        confirmVerified(acknowledged)
        null
    } catch (e: AssertionError) {
        e.message
    }
    if (unverified?.contains("Not verified calls:\n1) $acknowledged.find(2, false)") != true) {
        return "Fail: verified marks of verifyOrder $unverified"
    }
    verifySequence {
        acknowledged.find(1)
        acknowledged.find(2)
        acknowledged.find(1)
    }
    confirmVerified(acknowledged)

    val owner = mock<Repo>()
    val door = mock<Door>()
    every { owner.door(1) } returns door
    every { door.open() } just Runs
    owner.door(1).open()
    verifySequence { owner.door(1).open() }
    verifyOrder { owner.door(1).open() }
    verifyAll { owner.door(1).open() }
    verifySequence { door.open() }
    failure("Inverse verification failed.\n\nVerified calls:\n1) $owner.door(1)\n2) $door.open()") {
        verifySequence(inverse = true) { owner.door(1).open() }
    }?.let { return it }
    confirmVerified(owner, door)

    val idle = mock<Repo>()
    verifyOrder { idle wasNot Called }
    verifySequence { idle wasNot Called }
    verifyAll { idle wasNot Called }
    failure(
        "Verification failed: $name should not be called:\n" +
            "1) $name.find(1, false)\n2) $name.save(a)\n3) $name.find(2, false)\n\n$CALL_SITES"
    ) {
        verifyOrder {
            idle wasNot Called
            repo wasNot Called
        }
    }?.let { return it }

    val counted = mock<Repo>()
    every { counted.find(any(), any()) } returns "found"
    counted.find(1)
    counted.find(2)
    var first = 0
    var second = 0
    val countedName = "$counted"
    failure(
        "Verification failed: calls are not exactly matching verification sequence\n\nMatchers: \n" +
            "+$countedName.find(matcher(), any())\n$countedName.find(matcher(), any())\n\n" +
            "Calls:\n1) $countedName.find(1, false)\n2) +$countedName.find(2, false)\n\n$CALL_SITES"
    ) {
        verifySequence {
            counted.find(match { first++; true }, any())
            counted.find(match { second++; it == 1 }, any())
        }
    }?.let { return it }
    if (first != 2 || second != 2) return "Fail: matchers called $first and $second times"

    return "OK"
}
