// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.coVerify
import io.lokki.every
import io.lokki.verify

interface Repo {
    fun find(id: Int): String
    override fun toString(): String
}

fun test(repo: Repo, other: Any, any: Any) {
    every { repo.toString() } returns "repo"
    every { repo.hashCode() } returns 1
    every { repo.equals(other) } returns true

    verify { repo.<!LOKKI_NOT_RECORDED!>toString<!>() }
    verify { repo.<!LOKKI_NOT_RECORDED!>hashCode<!>() }
    verify { any.<!LOKKI_NOT_RECORDED!>hashCode<!>() }
    verify {
        repo.find(1)
        repo.<!LOKKI_NOT_RECORDED!>equals<!>(other)
    }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>repo == other<!> }
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>"$repo"<!> }
    verify { repo.find(1) }
    coVerify { repo.<!LOKKI_NOT_RECORDED!>toString<!>() }
    coVerify { repo.find(1) }
}
