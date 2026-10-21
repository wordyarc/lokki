package io.lokki

import io.lokki.internal.AnyMatcher
import io.lokki.internal.TestMock
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LokkiScopeThreadTest {
    @Test
    fun testClosedScopeRollsBackRecordsOfOtherThreads() {
        lokkiScope {
            val repo = TestMock("repo", FIND)
            repo.`lokki$state`.stub<String>(FIND, arrayOf(AnyMatcher)) returns "found"
            lokkiScope {
                thread { repo.`lokki$state`.answer(FIND_MEMBER, arrayOf(1)) }.join()
                assertEquals(1, repo.`lokki$state`.records().size)
            }
            assertEquals(0, repo.`lokki$state`.records().size)
        }
    }

    @Test
    fun testCallWithoutAnswerInOtherThreadFailsScope() {
        val error = runCatching {
            lokkiScope {
                val repo = TestMock("repo", FIND)
                thread { runCatching { repo.`lokki$state`.answer(FIND_MEMBER, arrayOf(1)) } }.join()
            }
        }.exceptionOrNull()

        assertEquals(AssertionError::class, error!!::class)
    }

    @Test
    fun testMockOfThreadWithoutScopeHasNoScope() {
        lokkiScope {
            var repo: TestMock? = null
            thread { repo = TestMock("repo", FIND) }.join()
            assertNull(repo!!.`lokki$state`.scope)
        }
    }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int)"
        const val FIND_MEMBER = 3
    }
}
