// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.Runs
import io.lokki.every
import io.lokki.just

class Tenant

interface Repo : Comparable<Repo> {
    var count: Int
    val names: MutableList<String>
    fun find(id: Int): String
    fun findOrNull(id: Int): String?
    fun save(name: String)
    operator fun get(index: Int): String
    operator fun set(index: Int, name: String)
    operator fun plusAssign(name: String)
    operator fun contains(name: String): Boolean
    operator fun invoke(name: String): Int

    context(tenant: Tenant)
    fun load(id: Int): String
}

fun test(repo: Repo, nullable: Repo?, tenant: Tenant, flag: Boolean) {
    every <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!> returns Unit
    every <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ repo.find(1); repo.find(2) }<!> returns "a"
    every <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{
        val id = 1
        repo.find(id)
    }<!> returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>nullable?.find(1)<!> } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo == repo<!> } returns true
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo === repo<!> } returns true
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo < repo<!> } returns true
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>"$repo"<!> } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>with(repo) { find(1) }<!> } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo.run { find(1) }<!> } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>context(tenant) { repo.load(1) }<!> } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo.count += 1<!> } just Runs
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo.count++<!> } returns 1
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>flag<!> } returns true
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>1<!> } returns 1
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>if (flag) repo.find(1) else repo.find(2)<!> } returns "a"
    every<String> { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>throw IllegalStateException()<!> } returns "a"

    every { repo.find(1) } returns "a"
    every { return@every repo.find(1) } returns "a"
    every { repo.find(1) as CharSequence } returns "a"
    every { repo.findOrNull(1)!! } returns "a"
    every { nullable!!.find(1) } returns "a"
    every { repo.count } returns 1
    every { repo.count = 1 } just Runs
    every { repo.save("a") } just Runs
    every { repo[1] } returns "a"
    every { repo[1] = "a" } just Runs
    every { repo += "a" } just Runs
    every { "a" in repo } returns true
    every { repo("a") } returns 1
    every { repo.compareTo(repo) } returns 0
    every { repo.equals(repo) } returns true
    every { repo.toString() } returns "a"
    with(tenant) { every { repo.load(1) } returns "a" }
}
