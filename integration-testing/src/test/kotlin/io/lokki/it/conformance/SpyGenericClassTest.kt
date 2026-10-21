package io.lokki.it.conformance

import io.lokki.mock
import io.lokki.spy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpyGenericClassTest {
    @Test
    fun bridgeMethods() {
        val view = mock<SomeView>()
        val childClazz = spy(ChildClazz())
        assertFailsWith<RuntimeException> { childClazz.foobar(view) }
        assertEquals(false, childClazz.called)
    }

    interface ParentView

    interface SomeView : ParentView

    interface ParentInterface<V : ParentView> {
        fun foobar(view: V)
    }

    abstract class ParentClazz<V : ParentView> : ParentInterface<V> {
        override fun foobar(view: V): Unit = throw RuntimeException()
    }

    open class ChildClazz : ParentClazz<SomeView>() {
        var called: Boolean = false

        override fun foobar(view: SomeView) {
            super.foobar(view)
            called = true
        }
    }
}
