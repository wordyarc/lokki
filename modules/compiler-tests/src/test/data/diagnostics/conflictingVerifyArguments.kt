// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.verify
import io.lokki.verifyCount

interface Repo {
    fun find(id: Int): String
}

fun test(repo: Repo, calls: Int) {
    verify(exactly = <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>1<!>, atLeast = 2) { repo.find(1) }
    verify(atMost = 3, exactly = <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>1<!>) { repo.find(1) }
    verify(exactly = <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>-2<!>) { repo.find(1) }
    verify(atLeast = <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>-1<!>) { repo.find(1) }
    verify(atLeast = 0, atMost = <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>-1<!>) { repo.find(1) }
    verify(atMost = <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>0<!>) { repo.find(1) }
    verify(atLeast = 3, atMost = <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>2<!>) { repo.find(1) }
    verify(false, 3, <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>2<!>) { repo.find(1) }
    verifyCount { <!LOKKI_CONFLICTING_VERIFY_ARGUMENTS!>-1<!> * { repo.find(1) } }

    verify(exactly = 0) { repo.find(1) }
    verify(exactly = -1) { repo.find(1) }
    verify(exactly = 2, atLeast = 1) { repo.find(1) }
    verify(atLeast = 0, atMost = 0) { repo.find(1) }
    verify(atLeast = 2, atMost = 2) { repo.find(1) }
    verify(atMost = Int.MAX_VALUE, exactly = 1) { repo.find(1) }
    verify(exactly = calls, atLeast = 2) { repo.find(1) }
    verify(atMost = calls) { repo.find(1) }
    verifyCount { 0 * { repo.find(1) } }
    verifyCount { calls * { repo.find(1) } }
    verifyCount { (2..1) * { repo.find(1) } }
}
