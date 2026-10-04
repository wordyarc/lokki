// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.verify

class Tenant

interface Repo {
    var limit: Int
    fun find(id: Int, limit: Int = 10, deleted: Boolean = false): String
    fun names(vararg names: String): Int
    fun String.width(padding: Int): Int

    context(tenant: Tenant)
    fun load(id: Int): String
}

fun test(repo: Repo, tenant: Tenant) {
    every { repo.find(1, <!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>) } returns "a"
    every { repo.find(any(), <!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>, <!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>) } returns "a"
    every { repo.find(limit = <!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>, id = 1) } returns "a"
    every { repo.find(and(<!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>, more(1))) } returns "a"
    every { repo.names("a", <!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>) } returns 1
    with(repo) { every { "a".width(<!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>) } returns 1 }
    with(tenant) { every { repo.load(<!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>) } returns "a" }

    every { repo.find(allAny()) } returns "a"
    every { repo.find(allAny(), 5) } returns "a"
    every { repo.find(limit = 5, id = allAny()) } returns "a"
    every { repo.names(allAny(), "b") } returns 1
    every { repo.limit = allAny() } returns Unit
    with(repo) { every { allAny<String>().width(1) } returns 1 }

    verify { repo.find(1, <!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>) }
    verify { repo.find(allAny()) }
}
