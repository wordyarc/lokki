// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.checkEquals
import io.lokki.every
import io.lokki.verify
import io.lokki.verifyCount

interface Repo {
    fun find(id: Int): String
    fun save(id: Int)
}

fun test(repo: Repo) {
    verify { repo.find(withArg { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!><!DSL_SCOPE_VIOLATION!>any<!><Int>()<!> }) }
    verify { repo.find(withArg { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>this@verify.any<Int>()<!> }) }
    verify { repo.find(withArg { checkEquals(1) }) }

    every { repo.find(any()) } answers {
        every { repo.save(<!DSL_SCOPE_VIOLATION!>firstArg<!>()) } returns Unit
        "found"
    }

    verifyCount {
        1 * {
            repo.find(1)
            2 <!DSL_SCOPE_VIOLATION, LOKKI_NOT_INTERCEPTABLE!>*<!> { repo.save(1) }
        }
    }
}
