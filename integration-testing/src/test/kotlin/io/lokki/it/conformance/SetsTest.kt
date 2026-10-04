package io.lokki.it.conformance

import io.lokki.every
import io.lokki.isMock
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetsTest {
    @Test
    fun returnsSetOfMocks() {
        val foo = mock<Foo>()
        every { foo.getTasks() } returns setOf(mock(), mock())

        val tasks = foo.getTasks()

        assertEquals(2, tasks.size)
        val task1 = tasks.first()
        val task2 = tasks.drop(1).first()

        assertTrue(isMock(task1))
        assertTrue(isMock(task2))
    }

    interface Foo {
        fun getTasks(): Set<Task>
    }

    interface Task
}
