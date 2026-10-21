package io.lokki

import io.lokki.internal.AnyMatcher
import io.lokki.internal.GeneratedMock
import io.lokki.internal.ObjectRedirect
import io.lokki.internal.Redirects
import io.lokki.internal.TestMock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RedirectsTest {
    private val clock = Any()

    private var installed: Any? = null

    private val redirect = ObjectRedirect(clock, "Clock", { spec -> TestMock(spec.name, NOW, flags = spec.flags) }) {
        installed = it
    }

    @Test
    fun testMockObjectNeedsScope() {
        assertEquals(
            "no active LokkiScope for mockObject(): use LokkiExtension or lokkiScope { }",
            assertFailsWith<LokkiException> { Redirects.mockObject(arrayOf(redirect)) }.message,
        )
        assertNull(installed)
    }

    @Test
    fun testScopeOwnsTheRedirect() {
        lokkiScope {
            Redirects.mockObject(arrayOf(redirect))
            val mock = assertNotNull(installed)
            assertTrue(isMock(clock))
            assertSame(mock, Redirects.of(clock, "Clock"))
            assertSame(mock, GeneratedMock.of(clock))
            assertTrue((mock as TestMock).`lokki$state`.isObject)
        }
        assertNull(installed)
        assertFalse(isMock(clock))
        assertEquals(
            "Clock is not mocked in the current scope; call mockObject(Clock)",
            assertFailsWith<LokkiException> { Redirects.of(clock, "Clock") }.message,
        )
    }

    @Test
    fun testRepeatedMockObjectInTheSameScopeReplacesTheMock() {
        lokkiScope {
            Redirects.mockObject(arrayOf(redirect))
            val first = installed
            Redirects.mockObject(arrayOf(redirect))
            assertNotSame(first, installed)
            assertSame(installed, Redirects.of(clock, "Clock"))
        }
    }

    @Test
    fun testNestedScopeUsesTheRedirectOfItsParent() {
        lokkiScope {
            Redirects.mockObject(arrayOf(redirect))
            val mock = installed
            lokkiScope {
                Redirects.mockObject(arrayOf(redirect))
                unmockObject(clock)
                unmockAll()
                assertSame(mock, installed)
            }
            assertSame(mock, installed)
            unmockObject(clock)
            assertNull(installed)
        }
    }

    @Test
    fun testBlockRemovesOnlyWhatItInstalled() {
        lokkiScope {
            Redirects.mockObject(arrayOf(redirect)) { assertNotNull(installed) }
            assertNull(installed)
            Redirects.mockObject(arrayOf(redirect))
            val mock = installed
            lokkiScope { Redirects.mockObject(arrayOf(redirect)) { assertSame(mock, installed) } }
            assertSame(mock, installed)
        }
    }

    @Test
    fun testConcurrentScopesCannotMockTheSameObject() {
        val first = LokkiScope.open(LokkiScope.root, "test first()")
        val second = LokkiScope.open(LokkiScope.root, "test second()")
        LokkiScope.withCurrent(first) { Redirects.mockObject(arrayOf(redirect)) }
        LokkiScope.withCurrent(second) {
            assertEquals(
                "Clock is already mocked in test first(); serialize these tests with @ResourceLock or @Isolated",
                assertFailsWith<LokkiException> { Redirects.mockObject(arrayOf(redirect)) }.message,
            )
            assertFailsWith<LokkiException> { Redirects.of(clock, "Clock") }
        }
        second.close()
        first.close()
        assertNull(installed)
    }

    @Test
    fun testClearAllMocksTellsObjectMocksFromRegularMocks() {
        lokkiScope {
            val regular = TestMock("regular", NOW)
            Redirects.mockObject(arrayOf(redirect))
            val redirected = installed as TestMock
            for (mock in listOf(regular, redirected)) mock.`lokki$state`.stub<Int>(NOW, arrayOf(AnyMatcher)) returns 5

            clearAllMocks(regularMocks = false, objectMocks = false)
            assertEquals(listOf(5, 5), listOf(regular, redirected).map { now(it) })
            clearAllMocks(regularMocks = false)
            assertEquals(5, now(regular))
            assertFailsWith<LokkiException> { now(redirected) }
            clearAllMocks(objectMocks = false)
            assertFailsWith<LokkiException> { now(regular) }
            clearAllMocks()
        }
    }

    private fun now(mock: TestMock, argument: Int = 1): Any? = mock.`lokki$state`.answer(NOW_MEMBER, arrayOf(argument))

    private companion object {
        const val NOW = "sample.Clock#now(kotlin.Int)"
        const val NOW_MEMBER = 3
    }
}
