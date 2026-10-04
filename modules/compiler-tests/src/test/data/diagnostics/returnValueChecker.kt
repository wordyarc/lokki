// RUN_PIPELINE_TILL: FRONTEND
// RETURN_VALUE_CHECKER_MODE: FULL

package sample

import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify

interface Repo {
    fun find(id: Int): String
    fun save(name: String)
}

fun test(): String {
    val repo = mock<Repo>()
    val names = slot<String>()
    every { repo.find(any()) } returns "a" andThen "b"
    every { repo.find(1) } returnsMany listOf("a", "b")
    every { repo.find(2) } answers { firstArg<Int>().toString() }
    every { repo.find(3) } throws IllegalStateException()
    every { repo.find(4) }.returns("a").andThen("b")
    every { repo.save(capture(names)) } just Runs
    mock<Repo> { every { find(1) } returns "a" }
    verify { repo.find(1) }
    verify(exactly = 1) { repo.save("a") }
    verify {
        repo.<!RETURN_VALUE_NOT_USED!>find<!>(1)
        repo.find(2)
    }
    repo.<!RETURN_VALUE_NOT_USED!>find<!>(5)
    return repo.find(6)
}
