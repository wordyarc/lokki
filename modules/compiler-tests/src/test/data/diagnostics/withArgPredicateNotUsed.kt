// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.VerificationScope
import io.lokki.checkEquals
import io.lokki.verify
import io.lokki.verifyCount

interface Repo {
    fun find(id: Int): String
    fun name(name: String?): String
    fun save(value: Any)
}

fun VerificationScope.two(): Int = withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it == 2<!> }

fun test(repo: Repo, ids: MutableList<Int>) {
    verify { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it == 2<!> }) }
    verify { repo.find(withArg { id -> <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>id != 2<!> }) }
    verify { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it > 2<!> }) }
    verify { repo.save(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it is String<!> }) }
    verify { repo.save(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it !is String<!> }) }
    verify { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it in 1..2<!> }) }
    verify { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it !in 1..2<!> }) }
    verify { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it > 1 && it < 3<!> }) }
    verify { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>!(it > 1)<!> }) }
    verify { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>true<!> }) }
    verify {
        repo.find(
            withArg {
                val doubled = it * 2
                <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>doubled == 4<!>
            }
        )
    }
    verify { repo.name(withNullableArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it == null<!> }) }
    verifyCount { 1 * { repo.find(withArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it == 2<!> }) } }
    verify { repo.find(coWithArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it == 2<!> }) }
    verify { repo.name(coWithNullableArg { <!LOKKI_WITH_ARG_PREDICATE_NOT_USED!>it == null<!> }) }

    verify { repo.find(withArg { ids.add(it) }) }
    verify { repo.find(withArg { checkEquals(2) }) }
    verify { repo.find(withArg { if (it != 2) throw AssertionError("not 2") }) }
    verify { repo.find(withArg { }) }
    verify { repo.find(withArg { return@withArg }) }
    verify { repo.find(match { it == 2 }) }
}
