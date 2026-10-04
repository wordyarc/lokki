// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.MatcherScope
import io.lokki.StubScope
import io.lokki.every

@JvmInline
value class Id(val value: Int)

interface Repo {
    fun find(id: Int, limit: Int = 10): String
    fun load(id: Id, limit: Int = 10): String
    fun String.label(width: Int = 2): String
    fun names(vararg names: String): Int
}

fun top(): String = ""

fun Repo.first(): String = find(1)

fun MatcherScope.anyId(): Id = any()

fun test(repo: Repo, nullable: Repo?, block: MatcherScope.() -> String) {
    <!LOKKI_UNSUPPORTED!>every(block)<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ repo.find(1); repo.find(2) }<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ nullable?.find(1) }<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ top() }<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ repo.first() }<!> returns "a"
    every { repo.<!LOKKI_UNSUPPORTED!>toString()<!> } returns "a"
    every { <!LOKKI_UNSUPPORTED!>any<Repo>()<!>.find(1) } returns "a"
    every { repo.find(<!LOKKI_UNSUPPORTED!>any<Int>() + 1<!>) } returns "a"
    every { repo.find(listOf(any<Int>()).<!LOKKI_UNSUPPORTED!>size<!>) } returns "a"
    every { repo.find(eq(<!LOKKI_UNSUPPORTED!>any()<!>)) } returns "a"
    every { repo.find(<!LOKKI_UNSUPPORTED!>return@every "b"<!>) } returns "a"
    every { repo.load(<!LOKKI_UNSUPPORTED!>any()<!>) } returns "a"
    every { repo.load(<!LOKKI_UNSUPPORTED!>anyId()<!>) } returns "a"
    with(repo) { every { <!LOKKI_UNSUPPORTED!>any<String>()<!>.label() } returns "a" }
    every { repo.names(*<!LOKKI_UNSUPPORTED!>any()<!>) } returns 1
    val reference: (MatcherScope.() -> String) -> StubScope<String> = <!LOKKI_UNSUPPORTED!>::every<!>
    val adapted: (MatcherScope.() -> String) -> Unit = <!LOKKI_UNSUPPORTED!>::every<!>
}
