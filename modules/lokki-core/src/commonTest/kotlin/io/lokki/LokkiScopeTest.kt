package io.lokki

import io.lokki.internal.AnyMatcher
import io.lokki.internal.EqMatcher
import io.lokki.internal.MockSpec
import io.lokki.internal.StaticInitializer
import io.lokki.internal.TestMock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class LokkiScopeTest {
    @Test
    fun testMockBelongsToCurrentScope() {
        assertNull(repo().`lokki$state`.scope)
        lokkiScope {
            val repo = repo()
            lokkiScope { assertNotSame(repo.`lokki$state`.scope, repo().`lokki$state`.scope) }
            assertEquals("lokkiScope", repo.`lokki$state`.scope.toString())
        }
    }

    @Test
    fun testClosedScopeRollsBackStubsAndRecordsOfOuterMocks() {
        lokkiScope {
            val repo = repo()
            stub(repo, AnyMatcher, "outer")
            find(repo, 1)
            lokkiScope {
                stub(repo, EqMatcher(2), "inner")
                assertEquals("inner", find(repo, 2))
                assertEquals(2, repo.`lokki$state`.records().size)
            }
            assertEquals("outer", find(repo, 2))
            assertEquals(listOf(1, 2), repo.`lokki$state`.records().map { it.arguments.single() })
        }
    }

    @Test
    fun testClosedScopeClearsItsMocks() {
        val repo = lokkiScope {
            repo().also { stub(it, AnyMatcher, "found") }
        }

        val message = assertFailsWith<LokkiException> { find(repo, 1) }.message!!

        assertContains(message, "no answer found for ${repo.`lokki$state`.name}.find(1)")
        assertContains(message, "\nThe mock belongs to lokkiScope, which is closed: the call came after its end")
    }

    @Test
    fun testClosedScopeRestoresAnswerPositionsOfOuterStubs() {
        lokkiScope {
            val repo = repo()
            repo.`lokki$state`.stub<String>(FIND, arrayOf(AnyMatcher)).returnsMany("a", "b", "c")
            assertEquals("a", find(repo, 1))
            lokkiScope { assertEquals(listOf("b", "c", "c"), List(3) { find(repo, 1) }) }
            assertEquals("b", find(repo, 1))
        }
    }

    @Test
    fun testClosedScopeRollsBackExclusionsAndChildMocksOfOuterMocks() {
        lokkiScope {
            val repo = repo()
            stub(repo, AnyMatcher, "found")
            val outerChild = link(repo, 1)
            val innerChild = lokkiScope {
                repo.`lokki$state`.exclude(FIND, arrayOf(EqMatcher(3)))
                find(repo, 3)
                assertEquals(0, repo.`lokki$state`.records().size)
                assertSame(outerChild, link(repo, 1))
                link(repo, 2).also { child -> lokkiScope { assertSame(child, link(repo, 2)) } }
            }
            assertNotSame(innerChild, link(repo, 2))
            assertSame(outerChild, link(repo, 1))
            find(repo, 3)
            assertEquals(1, repo.`lokki$state`.records().size)
        }
    }

    @Test
    fun testCallWithoutAnswerFailsScope() {
        lateinit var repo: TestMock

        val error = assertFailsWith<AssertionError> {
            lokkiScope {
                repo = repo()
                stub(repo, EqMatcher(1), "found")
                assertEquals("found", find(repo, 1))
                runCatching { find(repo, 2) }
                runCatching { find(repo, 3) }
            }
        }

        val name = repo.`lokki$state`.name
        assertEquals(
            "2 calls to mocks had no answer:\n1) $name.find(2)\n2) $name.find(3)\n\n" +
                "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true",
            error.message,
        )
        assertContains((error.cause as LokkiException).message!!, "no answer found for $name.find(2)")
    }

    @Test
    fun testCallWithoutAnswerIsSuppressedWhenScopeFails() {
        val failure = assertFailsWith<IllegalStateException> {
            lokkiScope {
                runCatching { find(repo(), 1) }
                error("test failed")
            }
        }

        assertEquals("test failed", failure.message)
        assertContains(failure.suppressedExceptions.single().message!!, "1 call to mocks had no answer:\n")
    }

    @Test
    fun testCallWithoutAnswerOnMockOfClosedScopeDoesNotFailAnotherScope() {
        val repo = lokkiScope { repo() }

        lokkiScope { runCatching { find(repo, 1) } }
    }

    @Test
    fun testOperationsWithoutMocksSeeTheWindow() {
        lokkiScope {
            val repo = repo()
            stub(repo, EqMatcher(1), "unused")
            stub(repo, EqMatcher(2), "used")
            find(repo, 2)
            lokkiScope {
                checkUnnecessaryStub()
                confirmVerified()
                val inner = repo()
                stub(inner, AnyMatcher, "inner")
                val unused = assertFailsWith<AssertionError> { checkUnnecessaryStub() }.message!!
                assertContains(unused, "\n\n1) ${inner.`lokki$state`.name}.find(any())")
                find(repo, 2)
                val unverified = assertFailsWith<AssertionError> { confirmVerified() }.message!!
                assertContains(unverified, "Verified call count: 0\nRecorded call count: 1\n")
                repo.`lokki$state`.let { it.markVerified(it.records().takeLast(1)) }
                inner.`lokki$state`.stub<String>(FIND, arrayOf(AnyMatcher)).returns("used")
                find(inner, 1)
                inner.`lokki$state`.let { it.markVerified(it.records()) }
                confirmVerified(clear = true)
                assertEquals(1, repo.`lokki$state`.records().size)
            }
            assertContains(assertFailsWith<AssertionError> { checkUnnecessaryStub() }.message!!, "find(eq(1))")
            assertFailsWith<AssertionError> { confirmVerified() }
        }
    }

    @Test
    fun testClearAllMocksClearsTheWindow() {
        lokkiScope {
            val repo = repo()
            repo.`lokki$state`.stub<String>(FIND, arrayOf(AnyMatcher)).returnsMany("a", "b")
            find(repo, 1)
            lokkiScope {
                stub(repo, EqMatcher(2), "inner")
                find(repo, 1)
                find(repo, 2)

                clearAllMocks()

                assertEquals(listOf(1), repo.`lokki$state`.records().map { it.arguments.single() })
                assertEquals("b", find(repo, 2))
                stub(repo, EqMatcher(3), "kept")
                clearAllMocks(answers = false)
                assertEquals("kept", find(repo, 3))
                assertEquals(2, repo.`lokki$state`.records().size)
            }
        }
    }

    @Test
    fun testStaticInitializerMocksAndStubsBelongToRoot() {
        lateinit var repo: TestMock
        lokkiScope {
            StaticInitializer.enter()
            try {
                repo = repo()
                stub(repo, AnyMatcher, "static")
            } finally {
                StaticInitializer.exit()
            }
            stub(repo, EqMatcher(2), "test")
            assertEquals("test", find(repo, 2))
        }

        assertSame(LokkiScope.root, repo.`lokki$state`.scope)
        assertEquals("static", find(repo, 2))
        assertEquals(1, repo.`lokki$state`.records().size)
    }

    @Test
    fun testMockOfScopeWithConcurrentTestsCannotBeStubbedOrVerified() {
        val outer = LokkiScope.open(LokkiScope.root, "class Outer")
        val repo = LokkiScope.withCurrent(outer) { repo() }
        val first = LokkiScope.open(outer, "test first()")
        val second = LokkiScope.open(outer, "test second()")

        val message = assertFailsWith<LokkiException> { stub(repo, AnyMatcher, "found") }.message
        assertFailsWith<LokkiException> { confirmVerified(repo) }
        lokkiScope { stub(repo(), AnyMatcher, "own") }

        assertEquals(
            "mock ${repo.`lokki$state`.name} of class Outer is used by concurrently running tests; " +
                "create it per test or serialize them with @ResourceLock",
            message,
        )
        second.close()
        LokkiScope.withCurrent(first) { stub(repo, AnyMatcher, "found") }
        first.close()
        outer.close()
    }

    @Test
    fun testScopeLifecycleErrors() {
        val outer = LokkiScope.open(LokkiScope.root, "class Outer")
        val inner = LokkiScope.open(outer, "test inner()")

        assertEquals(
            "class Outer cannot be closed before its nested scopes: test inner()",
            assertFailsWith<LokkiException> { outer.close() }.message,
        )
        assertEquals(
            "the root scope cannot be closed",
            assertFailsWith<LokkiException> { LokkiScope.root.close() }.message,
        )
        inner.close()
        inner.close()
        outer.close()
        assertEquals(
            "test late() cannot be opened in class Outer, which is closed",
            assertFailsWith<LokkiException> { LokkiScope.open(outer, "test late()") }.message,
        )
    }

    @Test
    fun testCurrentScopeIsRestored() {
        lokkiScope {
            val outer = repo().`lokki$state`.scope
            assertFailsWith<IllegalStateException> { lokkiScope { error("failed") } }
            assertSame(outer, repo().`lokki$state`.scope)
        }
        assertNull(repo().`lokki$state`.scope)
    }

    private fun repo(name: String = "repo"): TestMock = TestMock(name, FIND)

    private fun stub(repo: TestMock, matcher: Matcher<*>, answer: String) {
        @Suppress("UNCHECKED_CAST")
        repo.`lokki$state`.stub<String>(FIND, arrayOf(matcher as Matcher<Any?>)) returns answer
    }

    private fun find(repo: TestMock, id: Int): Any? = repo.`lokki$state`.answer(FIND_MEMBER, arrayOf(id))

    private fun link(repo: TestMock, id: Int): Any =
        repo.`lokki$state`.link(FIND, arrayOf(EqMatcher(id)), stubs = false) { spec: MockSpec ->
            TestMock(spec.name, FIND, flags = spec.flags)
        }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int)"
        const val FIND_MEMBER = 3
    }
}
