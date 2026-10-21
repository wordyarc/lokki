package io.lokki

import io.lokki.internal.AnyMatcher
import io.lokki.internal.EqMatcher
import io.lokki.internal.TestMock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerificationStateTest {
    private val repo = TestMock("repo", FIND)
    private val other = TestMock("other", FIND)

    init {
        for (mock in listOf(repo, other)) mock.`lokki$state`.stub<String>(FIND, arrayOf(AnyMatcher)) returns "found"
    }

    @Test
    fun testConfirmVerifiedClearsOnlyPassedMocks() {
        find(repo, 1)
        find(other, 1)
        for (mock in listOf(repo, other)) mock.`lokki$state`.let { it.markVerified(it.records()) }

        confirmVerified(repo, other)
        confirmVerified(repo, clear = true)

        assertEquals(emptyList(), repo.`lokki$state`.records())
        assertEquals(1, other.`lokki$state`.records().size)
    }

    @Test
    fun testConfirmVerifiedReportsUnverifiedCalls() {
        find(other, 2)

        val message = assertFailsWith<AssertionError> { confirmVerified(repo, other, clear = true) }.message!!

        assertContains(message, "Not verified calls:\n1) ${other.`lokki$state`.name}.find(2)\n")
        assertEquals(1, other.`lokki$state`.records().size)
    }

    @Test
    fun testClearMocksClearsEverythingByDefault() {
        find(repo, 1)
        repo.`lokki$state`.exclude(FIND, arrayOf(EqMatcher(2)), current = false)

        clearMocks(repo)

        assertEquals(emptyList(), repo.`lokki$state`.records())
        assertFailsWith<LokkiException> { find(repo, 2) }
        assertEquals(1, repo.`lokki$state`.records().size)
    }

    @Test
    fun testClearMocksKeepsDeselectedState() {
        find(repo, 1)

        clearMocks(repo, other, recordedCalls = false)

        assertEquals(1, repo.`lokki$state`.records().size)
        assertFailsWith<LokkiException> { find(repo, 1) }
    }

    @Test
    fun testCheckUnnecessaryStub() {
        find(repo, 1)

        checkUnnecessaryStub(repo)
        assertContains(
            assertFailsWith<AssertionError> { checkUnnecessaryStub(repo, other) }.message!!,
            "1) ${other.`lokki$state`.name}.find(any())",
        )
    }

    @Test
    fun testOperationsWithoutMocksNeedScope() {
        assertEquals(noScope("confirmVerified"), assertFailsWith<LokkiException> { confirmVerified() }.message)
        assertEquals(noScope("checkUnnecessaryStub"), assertFailsWith<LokkiException> { checkUnnecessaryStub() }.message)
        assertEquals(noScope("clearAllMocks"), assertFailsWith<LokkiException> { clearAllMocks() }.message)
        find(repo, 1)

        assertEquals("text is not a lokki mock", assertFailsWith<LokkiException> { clearMocks(repo, "text") }.message)
        assertEquals(1, repo.`lokki$state`.records().size)
    }

    private fun noScope(function: String): String =
        "no active LokkiScope for $function(): use LokkiExtension or lokkiScope { }"

    private fun find(mock: TestMock, id: Int) {
        mock.`lokki$state`.answer(FIND_MEMBER, arrayOf(id))
    }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int)"
        const val FIND_MEMBER = 3
    }
}
