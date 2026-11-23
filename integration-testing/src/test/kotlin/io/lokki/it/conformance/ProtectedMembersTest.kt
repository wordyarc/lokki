package io.lokki.it.conformance

import io.lokki.every
import io.lokki.it.mockable.Template
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectedMembersTest {
    abstract class Report {
        fun render(): String = "report:" + body("title")

        protected abstract fun body(title: String): String
    }

    @Test
    fun protectedAbstractMemberIsStubbedByName() {
        val report = mock<Report>()
        every { report["body"](any<String>()) } answers { firstArg<String>().uppercase() }

        assertEquals("report:TITLE", report.render())
        verify { report["body"]("title") }
    }

    @Test
    fun protectedMemberOfMockableClassIsStubbedByName() {
        val template = mock<Template>()
        every { template.run() } answers { callOriginal() }
        every { template["hook"](1) } returns "stub"

        assertEquals("result=stub", template.run())
        verify { template["hook"](any<Int>()) }
    }

    @Test
    fun originalOfProtectedMemberIsCalled() {
        val template = mock<Template>()
        every { template.run() } answers { callOriginal() }
        every { template["hook"](any<Int>()) } answers { callOriginal() }

        assertEquals("result=real:1", template.run())
    }
}
