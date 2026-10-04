// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.MatcherScope
import io.lokki.VerificationScope
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.verify
import io.lokki.verifyCount

interface Repo {
    fun find(id: Int): String
}

fun test(repo: Repo, block: MatcherScope.() -> String, check: VerificationScope.() -> Any?) {
    every(<!LOKKI_STUB_BLOCK_NOT_LITERAL!>block<!>) returns "a"
    every(stubBlock = <!LOKKI_STUB_BLOCK_NOT_LITERAL!>block<!>) returns "a"
    every(<!LOKKI_STUB_BLOCK_NOT_LITERAL!>repo::stub<!>) returns "a"
    every({ repo.find(any()) }) returns "a"
    every(stubBlock = { repo.find(any()) }) returns "a"
    every(fun MatcherScope.(): String { return repo.find(any()) }) returns "a"

    verify(verifyBlock = <!LOKKI_STUB_BLOCK_NOT_LITERAL!>check<!>)
    verify(exactly = 1, verifyBlock = <!LOKKI_STUB_BLOCK_NOT_LITERAL!>check<!>)
    verify { repo.find(any()) }
    verify(verifyBlock = { repo.find(any()) }, exactly = 1)

    excludeRecords(excludeBlock = <!LOKKI_STUB_BLOCK_NOT_LITERAL!>block<!>)
    excludeRecords(false, <!LOKKI_STUB_BLOCK_NOT_LITERAL!>block<!>)
    excludeRecords(excludeBlock = { repo.find(any()) }, current = false)
    verifyCount { 2.times(<!LOKKI_STUB_BLOCK_NOT_LITERAL!>check<!>) }
    verifyCount { 2.times { repo.find(any()) } }
}

fun Repo.stub(scope: MatcherScope): String = find(scope.any())
