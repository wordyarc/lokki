// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.Called
import io.lokki.verify
import io.lokki.verifyCount

interface Repo {
    val self: Repo
    fun find(id: Int): String
    fun child(): Repo
    fun nullableChild(): Repo?
}

class Holder(val repo: Repo) {
    fun repo(): Repo = repo

    fun test() {
        verify { repo wasNot Called }
        verify { repo() wasNot Called }
    }
}

fun mocks(repo: Repo): List<Repo> = listOf(repo)

fun test(repo: Repo, holder: Holder) {
    verify { <!LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT!>repo.find(1)<!> wasNot Called }
    verify { <!LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT!>repo.child()<!> wasNot Called }
    verify { <!LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT!>repo.nullableChild()!!<!> wasNot Called }
    verify { <!LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT!>holder.repo()<!> wasNot Called }
    verify {
        repo.find(1)
        <!LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT!>repo.child()<!> wasNot Called
    }
    verifyCount { 1 * { <!LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT!>repo.child()<!> wasNot Called } }

    verify { repo wasNot Called }
    verify { repo.self wasNot Called }
    verify { holder.repo wasNot Called }
    verify { listOf(repo, holder.repo) wasNot Called }
    verify { mocks(repo) wasNot Called }
}
