package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class HierarchicalMockingTest {
    interface Goal {
        fun getRootTask(): Task
    }

    interface Task {
        fun getSubTask(): Task

        fun doIt(): Int
    }

    @Test
    fun hierarchicalMocking() {
        val foo =
            mock<Goal> {
                every { getRootTask() } returns
                    mock {
                        every { doIt() } returns 5
                    } andThen
                    mock<Task> {
                        every { doIt() } returns 6 andThen 7

                        every { getSubTask() } returns
                            mock {
                                every { doIt() } returns 8 andThen 9
                            }
                    }
            }

        assertEquals(5, foo.getRootTask().doIt())
        assertEquals(6, foo.getRootTask().doIt())
        assertEquals(7, foo.getRootTask().doIt())
        assertEquals(8, foo.getRootTask().getSubTask().doIt())
        assertEquals(9, foo.getRootTask().getSubTask().doIt())
    }
}
