// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.Runs
import io.lokki.StubScope
import io.lokki.coEvery
import io.lokki.coJustRun
import io.lokki.every
import io.lokki.just
import io.lokki.justRun
import io.lokki.mock

interface Repo {
    fun find(id: Int): String
    fun save(name: String)
    suspend fun load(id: Int): String
    suspend fun store(name: String)
}

fun <R> twice(block: () -> R): R {
    block()
    return block()
}

fun <R> collect(block: () -> R): List<R> = listOf(block())

fun consume(block: () -> Any?) {
    block()
}

fun expression(repo: Repo) = every { repo.find(1) }

fun returned(repo: Repo): StubScope<String> {
    return every { repo.find(1) }
}

class Fixture(repo: Repo) {
    val stub = every { repo.find(1) }

    init {
        <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(2) }
    }
}

fun test(repo: Repo, nullable: Repo?, flag: Boolean, ids: List<Int>) {
    <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) }
    with(repo) { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { find(1) } }
    with(receiver = repo, block = { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { find(1) } })
    repo.run { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { find(1) } }
    repo.let { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { it.find(1) } }
    repo.apply { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { find(1) } }
    repo.also { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { it.find(1) } }
    nullable?.let { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { it.find(1) } }
    run { return@run <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) } }
    run {
        <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) }
        every { repo.find(2) } returns "a"
    }
    synchronized(repo) { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) } }
    twice { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) } }
    mock<Repo> { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { find(1) } }
    ids.forEach { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(it) } }
    for (id in ids) <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(id) }
    while (flag) {
        <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) }
    }
    if (flag) <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) }
    if (flag) <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) } else <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(2) }
    when {
        flag -> <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) }
        else -> <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(2) }
    }
    try {
        <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) }
    } catch (e: IllegalStateException) {
        <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(2) }
    } finally {
        <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(3) }
    }
    val unit: () -> Unit = { <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) } }
    val guarded = try {
        every { repo.find(1) }
    } finally {
        <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(2) }
    }

    every { repo.find(1) } returns "a"
    every { repo.find(1) }.returns("a")
    every { repo.save("a") } just Runs
    val stub = every { repo.find(1) }
    stub returns "a"
    val scoped = with(repo) { every { find(1) } }
    with(repo) { every { find(1) } } returns "a"
    with(repo) { every { find(1) } returns "a" }
    val chosen = if (flag) every { repo.find(1) } else every { repo.find(2) }
    val deferred: () -> StubScope<String> = { every { repo.find(1) } }
    consume { every { repo.find(1) } }
    collect { every { repo.find(1) } }
    ids.map { every { repo.find(it) } }
    listOf(every { repo.find(1) })
    <!LOKKI_STUB_WITHOUT_ANSWER!>coEvery<!> { repo.load(1) }
    coEvery { repo.load(1) } returns "a"
    justRun { repo.save("a") }
    coJustRun { repo.store("a") }
    justRun { repo.save("a") } andThenThrows IllegalStateException()
    <!LOKKI_STUB_WITHOUT_ANSWER!>every<!> { repo.find(1) }
}
