// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.excludeRecords

interface Repo {
    fun find(id: Int): String
    override fun toString(): String
}

fun test(repo: Repo, other: Any) {
    excludeRecords { repo.<!LOKKI_USELESS_EXCLUSION!>hashCode<!>() }
    excludeRecords { repo.<!LOKKI_USELESS_EXCLUSION!>toString<!>() }
    excludeRecords(current = false) {
        repo.find(1)
        repo.<!LOKKI_USELESS_EXCLUSION!>equals<!>(other)
    }
    excludeRecords { other.<!LOKKI_USELESS_EXCLUSION!>hashCode<!>() }
    excludeRecords { repo.find(any()) }
}
