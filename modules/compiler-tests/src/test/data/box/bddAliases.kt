// WITH_KOTLINX_COROUTINES
package sample

import io.lokki.Ordering
import io.lokki.bdd.coGiven
import io.lokki.bdd.coThen
import io.lokki.bdd.given
import io.lokki.bdd.then
import io.lokki.mock
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Repo {
    fun find(id: Int): String

    suspend fun load(id: Int): String
}

fun box(): String {
    val repo = mock<Repo>()
    given { repo.find(any()) } returns "found"
    coGiven { repo.load(1) } returns "loaded"
    if (repo.find(1) != "found") return "Fail given"
    var loaded = ""
    suspend { loaded = repo.load(1) }.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() })
    if (loaded != "loaded") return "Fail coGiven"
    then(exactly = 1) { repo.find(1) }
    then(ordering = Ordering.ORDERED) { repo.find(1) }
    coThen { repo.load(1) }
    return "OK"
}
