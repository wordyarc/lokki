package io.lokki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssertScopeTest {
    @Test
    fun testCheckEqualsComparesArraysByContent() {
        AssertScope(intArrayOf(1, 2)).checkEquals(intArrayOf(1, 2))
        AssertScope(null).checkEquals(null)

        assertEquals(
            "expected [[1, 3]] but found [[1, 2]]",
            assertFailsWith<AssertionError> { AssertScope(intArrayOf(1, 2)).checkEquals(intArrayOf(1, 3)) }.message,
        )
        assertEquals(
            "id expected [1] but found [null]",
            assertFailsWith<AssertionError> { AssertScope(null).checkEquals("id", 1) }.message,
        )
    }
}
