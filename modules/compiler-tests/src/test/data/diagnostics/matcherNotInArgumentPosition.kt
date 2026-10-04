// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.slot

interface Repo {
    var limit: Int
    fun find(id: Int, limit: Int = 10): String
    fun label(text: String?): String
    fun String.width(padding: Int): Int
    fun names(vararg names: String): Int
    fun accept(scope: MatcherScope): Int
}

fun MatcherScope.small(): Int = less(5)

fun MatcherScope.twice(value: Int): Int = value * 2

fun MatcherScope.computed(block: () -> Int): Int = block()

val MatcherScope.positive: Int
    get() = more(0)

context(scope: MatcherScope)
fun contextual(): Int = scope.any()

fun plain(value: Int): Int = value

fun test(repo: Repo) {
    every { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Int>()<!> } returns 1
    every { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>small()<!> } returns 1
    every { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Repo>()<!>.find(1) } returns "a"
    every { repo.find(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Int>()<!> + 1) } returns "a"
    every { repo.find(listOf(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Int>()<!>).size) } returns "a"
    every { repo.find(plain(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any()<!>)) } returns "a"
    every { repo.find(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>small()<!> + <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>positive<!>) } returns "a"
    every { repo.find(eq(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any()<!>)) } returns "a"
    every { repo.find(more(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>small()<!>)) } returns "a"
    every { repo.find(twice(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any()<!>)) } returns "a"
    every { repo.find(computed { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any()<!> }) } returns "a"
    every { repo.find(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Int>()<!> as Int) } returns "a"
    every { repo.find(if (repo.limit > 0) <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any()<!> else 1) } returns "a"
    every { repo.find(and(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Int>()<!> + 1, not(plain(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>small()<!>)))) } returns "a"
    every { repo.limit = <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Int>()<!> + 1 } returns Unit
    every { repo.names(*arrayOf(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any()<!>)) } returns 1
    every { repo.names("a", <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<String>()<!> + "b") } returns 1
    every { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>repo.accept(this)<!> } returns 1
    with(repo) { every { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<String>()<!>.plus("a").width(1) } returns 1 }

    val ids = slot<Int>()
    every { repo.find(allAny(), any()) } returns "a"
    every { repo.find(id = any(), limit = eq(1)) } returns "a"
    every { repo.find(limit = more(1), id = less(2, andEquals = true)) } returns "a"
    every { repo.find(and(more(1), or(small(), not(positive)))) } returns "a"
    every { repo.find(capture(ids), range(1, 10)) } returns "a"
    every { repo.find(twice(2), computed { 3 }) } returns "a"
    every { repo.find(contextual()) } returns "a"
    every { repo.find(match { it > 0 }) } returns "a"
    every { repo.find(this.any(), this@every.any()) } returns "a"
    every { repo.find(plain(1) + 1) } returns "a"
    every { repo.label(isNull()) } returns "a"
    every { repo.limit = any() } returns Unit
    every { repo.names("a", any(), *arrayOf("b")) } returns 1
    with(repo) { every { any<String>().width(more(1)) } returns 1 }
}
