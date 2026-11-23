// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.verify
import io.lokki.context.withContext

class Tenant(val name: String)

interface Repo {
    context(tenant: Tenant)
    fun load(id: Int): String

    context(tenant: Tenant)
    fun child(): Repo

    fun find(name: String): String
}

fun test(repo: Repo, tenant: Tenant, block: context(Tenant) () -> Any?) {
    every { withContext<Tenant>(<!LOKKI_STUB_BLOCK_NOT_LITERAL!>block<!>) } returns "a"
    every { withContext<Tenant> <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!> } returns "a"
    every { withContext<Tenant> <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ repo.load(1); repo.load(2) }<!> } returns "a"
    every { withContext<Tenant> { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo.load(1) == "a"<!> } } returns true
    every { withContext<Tenant> { repo.find(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>contextOf<Tenant>()<!>.name) } } returns "a"
    every { withContext<Tenant> { repo.child().load(1) } } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>context(tenant) { repo.load(1) }<!> } returns "a"
    verify { withContext<Tenant> <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!> }

    every { withContext<Tenant> { repo.load(any()) } } returns "a"
    every { withContext<Tenant> { repo.load(1) as CharSequence } } returns "a"
    every { withContext<Tenant> { repo.child() } } returns repo
    verify { withContext<Tenant> { repo.load(any()) } }
    verify {
        withContext<Tenant> { repo.load(1) }
        repo.find("a")
    }
    excludeRecords { withContext<Tenant> { repo.load(any()) } }
}
