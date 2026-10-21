package io.lokki.it.conformance

import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.every
import io.lokki.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnumTests {
    open class ClsWithEnum {
        open var type: EnumType? = null

        enum class EnumType {
            ONE,
            TWO,
            THREE,
        }

        open var typeString = "test"
    }

    @Mock
    lateinit var testObj: ClsWithEnum

    @BeforeTest
    fun setUp() = MockAnnotations.init(this)

    @Test
    fun testMockingEnumMemberInClass() {
        val mockedClass = mock<ClsWithEnum>()
        every { mockedClass.type } returns ClsWithEnum.EnumType.ONE
        assertEquals(ClsWithEnum.EnumType.ONE, mockedClass.type, "Enum returned does not match mocked response")
    }

    @Test
    fun testMockingEnumMemberInLateinitClass() {
        every { testObj.type } returns ClsWithEnum.EnumType.ONE
        assertEquals(ClsWithEnum.EnumType.ONE, testObj.type, "Enum returned does not match mocked response")
    }

    @Test
    fun testMockingStringMemberInLateinitClass() {
        every { testObj.typeString } returns "teststring"
        assertEquals("teststring", testObj.typeString, "Enum returned does not match mocked response")
    }
}
