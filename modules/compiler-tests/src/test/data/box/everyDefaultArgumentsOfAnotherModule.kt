// MODULE: lib
// FILE: Repo.kt
package lib

import io.lokki.mock

interface Repo {
    fun defaultLimit(): Int
    fun find(id: Int, includeDeleted: Boolean = false, limit: Int = defaultLimit()): String
    fun rename(id: Int, name: String, suffix: String = "!"): String
}

fun newRepo(): Repo = mock<Repo>()

// MODULE: main(lib)
// FILE: main.kt
package app

import io.lokki.LokkiException
import io.lokki.every
import lib.newRepo

private fun answer(block: () -> String): String =
    try {
        block()
    } catch (e: LokkiException) {
        e.message!!
    }

fun box(): String {
    val repo = newRepo()
    every { repo.defaultLimit() } returns 10

    every { repo.find(any()) } returns "found"
    if (repo.find(1) != "found") return "Fail: omitted defaults"
    if (repo.find(2, false, 10) != "found") return "Fail: explicit defaults"
    if (answer { repo.find(1, includeDeleted = true) } != "no answer found for Repo.find") return "Fail: includeDeleted"
    if (answer { repo.find(1, limit = 5) } != "no answer found for Repo.find") return "Fail: limit"

    every { repo.rename(any(), any()) } returns "renamed"
    if (repo.rename(1, "a") != "renamed") return "Fail: default after a reference matcher"
    if (answer { repo.rename(1, "a", "?") } != "no answer found for Repo.rename") return "Fail: suffix"

    return "OK"
}
