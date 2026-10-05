package io.lokki.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstanceAllocatorTest {
    open class Engine(val power: Int) {
        val name: String = "engine"

        init {
            error("the constructor must not run")
        }
    }

    class EngineMock : Engine(1)

    @Test
    fun testAllocatesWithoutConstructors() {
        val engine = InstanceAllocator.allocate(EngineMock::class.java)

        assertEquals(EngineMock::class.java, engine.javaClass)
        assertEquals(0, engine.power)
        assertNull(engine.name as String?)
    }
}
