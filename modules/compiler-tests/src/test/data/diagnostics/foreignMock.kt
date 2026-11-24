// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// FILE: MockBean.kt
package org.springframework.boot.test.mock.mockito

@Target(AnnotationTarget.FIELD)
annotation class MockBean

// FILE: MockitoSpyBean.kt
package org.springframework.test.context.bean.override.mockito

@Target(AnnotationTarget.FIELD)
annotation class MockitoSpyBean

// FILE: test.kt
package sample

import io.lokki.every
import io.lokki.verify
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

interface Repo {
    fun find(id: Int): String
}

class ServiceTest {
    @MockBean
    lateinit var repo: Repo

    @MockitoSpyBean
    lateinit var spied: Repo

    fun test() {
        every { <!LOKKI_FOREIGN_MOCK!>repo<!>.find(1) } returns "a"
        verify { <!LOKKI_FOREIGN_MOCK!>spied<!>.find(1) }
    }
}
