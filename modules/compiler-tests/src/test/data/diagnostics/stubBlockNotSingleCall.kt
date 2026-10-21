// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.Runs
import io.lokki.coExcludeRecords
import io.lokki.coVerify
import io.lokki.coVerifyCount
import io.lokki.coVerifySequence
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.just
import io.lokki.verify
import io.lokki.verifyAll
import io.lokki.verifyCount
import io.lokki.verifyOrder

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

fun testVerify(repo: Repo, nullable: Repo?, flag: Boolean) {
    verify <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!>
    verify {
        repo.find(1)
        <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>val id = 2<!>
        repo.find(id)
    }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>nullable?.find(1)<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo == repo<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo < repo<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>"$repo"<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo.count += 1<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>with(repo) { find(1) }<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>if (flag) repo.find(1) else repo.find(2)<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>for (id in 1..2) repo.find(id)<!> }
    verifyOrder { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>if (flag) repo.find(1) else repo.find(2)<!> }
    verifyAll <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!>
    coVerifySequence { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>for (id in 1..2) repo.find(id)<!> }

    verify {
        repo.find(1)
        repo.find(2)
    }
    verify { repo.find(1) as CharSequence }
    verify { repo.count = 1 }
    verify { repo[1] = "a" }
    verify { repo += "a" }
    verify { repo.compareTo(repo) }
    verifyOrder {
        repo.find(1)
        repo.find(2)
    }
}

fun testExclude(repo: Repo, nullable: Repo?, flag: Boolean) {
    excludeRecords <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!>
    excludeRecords {
        repo.find(1)
        <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>val id = 2<!>
        repo.find(id)
    }
    excludeRecords { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>nullable?.find(1)<!> }
    excludeRecords { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo == repo<!> }
    excludeRecords { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>"$repo"<!> }
    excludeRecords { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo.count += 1<!> }
    excludeRecords { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>if (flag) repo.find(1) else repo.find(2)<!> }
    excludeRecords { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>for (id in 1..2) repo.find(id)<!> }
    verifyCount { 1 * <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!> }
    verifyCount { 1 * { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>for (id in 1..2) repo.find(id)<!> } }
    coVerify <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>{ }<!>
    coVerify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo == repo<!> }
    coExcludeRecords { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>if (flag) repo.find(1) else repo.find(2)<!> }
    coVerifyCount { 1 * { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>for (id in 1..2) repo.find(id)<!> } }

    excludeRecords {
        repo.find(1)
        repo.find(2)
    }
    excludeRecords { repo.count = 1 }
    excludeRecords { repo[1] = "a" }
    excludeRecords { repo += "a" }
    verifyCount {
        1 * { repo.find(1) }
        (1..2) * {
            repo.find(1)
            repo.find(2)
        }
    }
    coVerify {
        repo.find(1)
        repo.count = 1
    }
    coExcludeRecords { repo[1] = "a" }
    coVerifyCount { (1..2) * { repo.find(1) } }
}
