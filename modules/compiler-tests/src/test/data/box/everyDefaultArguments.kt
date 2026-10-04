// CHECK_BYTECODE_TEXT
// PARAMETERS_METADATA

package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock

interface Repo {
    fun defaultLimit(): Int
    fun find(id: Int, includeDeleted: Boolean = false, limit: Int = defaultLimit()): String
    fun rename(id: Int, name: String, suffix: String = "!"): String
    fun page(offset: Int, size: Int = offset * 2): String
}

private fun answer(block: () -> String): String =
    try {
        block()
    } catch (e: LokkiException) {
        e.message!!
    }

fun box(): String {
    val repo = mock<Repo>()
    every { repo.defaultLimit() } returns 10

    every { repo.find(any()) } returns "found"
    if (repo.find(1) != "found") return "Fail: omitted defaults"
    if (repo.find(2, false, 10) != "found") return "Fail: explicit defaults"
    if (answer { repo.find(1, includeDeleted = true) } != "no answer found for Repo.find") return "Fail: includeDeleted"
    if (answer { repo.find(1, limit = 5) } != "no answer found for Repo.find") return "Fail: limit"

    every { repo.rename(any(), any()) } returns "renamed"
    if (repo.rename(1, "a") != "renamed") return "Fail: default after a reference matcher"
    if (answer { repo.rename(1, "a", "?") } != "no answer found for Repo.rename") return "Fail: suffix"

    every { repo.page(3) } returns "page"
    if (repo.page(3) != "page" || repo.page(3, 6) != "page") return "Fail: default that depends on an argument"
    if (answer { repo.page(3, 7) } != "no answer found for Repo.page") return "Fail: size"

    val log = StringBuilder()
    val receiver = { log.append("receiver;"); repo }
    val id = { log.append("id;"); 7 }
    every { receiver().find(id()) } returns "seven"
    if (log.toString() != "receiver;id;") return "Fail: evaluation order $log"
    if (repo.find(7) != "seven" || repo.find(8) != "found") return "Fail: the last matching stub answers"

    return "OK"
}

// 0 checkNotNullParameter
// 1 LOCALVARIABLE name Ljava/lang/String;
// 1 LOCALVARIABLE suffix Ljava/lang/String;
// 1 parameter synthetic  name
// 1 parameter synthetic  suffix
