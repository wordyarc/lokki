package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock

interface Api {
    fun search(query: String, limit: Int, strict: Boolean = false): String
}

private fun answer(block: () -> String): String =
    try {
        block()
    } catch (e: LokkiException) {
        "none"
    }

fun box(): String {
    val api = mock<Api>()
    val log = StringBuilder()
    fun <T> logged(name: String, value: T): T {
        log.append(name).append(';')
        return value
    }

    every { api.search(limit = 10, query = any()) } returns "ten"
    if (api.search("a", 10) != "ten") return "Fail: matcher in a reordered argument"
    if (answer { api.search("a", 11) } != "none") return "Fail: value in a reordered argument"
    if (answer { api.search("a", 10, strict = true) } != "none") return "Fail: default of a reordered call"

    every { logged("receiver", api).search(limit = logged("limit", 5), query = logged("query", "q")) } returns "five"
    if (log.toString() != "receiver;limit;query;") return "Fail: evaluation order of values $log"
    if (api.search("q", 5) != "five" || answer { api.search("q", 6) } != "none") return "Fail: reordered values"

    log.clear()
    every {
        api.search(strict = logged("strict", true), limit = more(logged("limit", 20)), query = eq(logged("query", "z")))
    } returns "strict"
    if (log.toString() != "strict;limit;query;") return "Fail: evaluation order of matcher arguments $log"
    if (api.search("z", 21, strict = true) != "strict") return "Fail: reordered matchers"
    if (answer { api.search("z", 21) } != "none" || answer { api.search("z", 20, true) } != "none") {
        return "Fail: reordered matchers of another call"
    }

    return "OK"
}
