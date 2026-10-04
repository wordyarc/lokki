// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.MatcherScope
import io.lokki.every

interface Repo {
    fun find(id: Int): String
}

fun test(repo: Repo, block: MatcherScope.() -> String) {
    every(<!LOKKI_STUB_BLOCK_NOT_LITERAL!>block<!>) returns "a"
    every(stubBlock = <!LOKKI_STUB_BLOCK_NOT_LITERAL!>block<!>) returns "a"
    every(<!LOKKI_STUB_BLOCK_NOT_LITERAL!>repo::stub<!>) returns "a"
    every({ repo.find(any()) }) returns "a"
    every(stubBlock = { repo.find(any()) }) returns "a"
    every(fun MatcherScope.(): String { return repo.find(any()) }) returns "a"
}

fun Repo.stub(scope: MatcherScope): String = find(scope.any())
