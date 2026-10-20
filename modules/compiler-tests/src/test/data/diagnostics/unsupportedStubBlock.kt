// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:Suppress(
    "LOKKI_STUB_BLOCK_NOT_LITERAL",
    "LOKKI_STUB_BLOCK_NOT_SINGLE_CALL",
    "LOKKI_NOT_INTERCEPTABLE",
    "LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION",
    "LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC",
    "LOKKI_NOT_RECORDED",
)

package sample

import io.lokki.Called
import io.lokki.MatcherScope
import io.lokki.StubScope
import io.lokki.VerificationScope
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.mock
import io.lokki.verify
import io.lokki.verifyCount

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

fun VerificationScope.unused(repo: Repo): Int {
    <!LOKKI_UNSUPPORTED!>repo wasNot Called<!>
    return any()
}

fun test(repo: Repo, nullable: Repo?, block: MatcherScope.() -> String, check: VerificationScope.() -> Any?) {
    <!LOKKI_UNSUPPORTED!>every(block)<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ repo.find(1); repo.find(2) }<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ nullable?.find(1) }<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ top() }<!> returns "a"
    every <!LOKKI_UNSUPPORTED!>{ repo.first() }<!> returns "a"
    every { <!LOKKI_UNSUPPORTED!>any<Repo>()<!>.find(1) } returns "a"
    every { repo.find(<!LOKKI_UNSUPPORTED!>any<Int>() + 1<!>) } returns "a"
    every { repo.find(listOf(any<Int>()).<!LOKKI_UNSUPPORTED!>size<!>) } returns "a"
    every { repo.find(eq(<!LOKKI_UNSUPPORTED!>any()<!>)) } returns "a"
    every { repo.find(<!LOKKI_UNSUPPORTED!>return@every "b"<!>) } returns "a"
    every { repo.load(<!LOKKI_UNSUPPORTED!>any()<!>) } returns "a"
    every { repo.load(<!LOKKI_UNSUPPORTED!>anyId()<!>) } returns "a"
    with(repo) { every { <!LOKKI_UNSUPPORTED!>any<String>()<!>.label() } returns "a" }
    every { repo.names(*<!LOKKI_UNSUPPORTED!>any()<!>) } returns 1
    every { repo.names(*anyVararg(), "a", *<!LOKKI_UNSUPPORTED!>varargAny { it.isEmpty() }<!>) } returns 1
    mock<Repo> { every { <!LOKKI_UNSUPPORTED!>toString()<!> } returns "a" }
    val reference: (MatcherScope.() -> String) -> StubScope<String> = <!LOKKI_UNSUPPORTED!>::every<!>
    val adapted: (MatcherScope.() -> String) -> Unit = <!LOKKI_UNSUPPORTED!>::every<!>

    <!LOKKI_UNSUPPORTED!>verify(verifyBlock = check)<!>
    verify <!LOKKI_UNSUPPORTED!>{ }<!>
    verify <!LOKKI_UNSUPPORTED!>{
        repo.find(1)
        nullable?.find(1)
    }<!>
    verify { repo.<!LOKKI_UNSUPPORTED!>toString()<!> }
    verify { <!LOKKI_UNSUPPORTED!>any<Repo>()<!>.find(1) }
    verify { repo.find(<!LOKKI_UNSUPPORTED!>any<Int>() + 1<!>) }
    verify { repo.find(withArg <!LOKKI_UNSUPPORTED!>{ this@verify.any<Int>() }<!>) }
    val verification: (Boolean, Int, Int, Int, VerificationScope.() -> Any?) -> Unit = <!LOKKI_UNSUPPORTED!>::verify<!>
    verify { <!LOKKI_UNSUPPORTED!>listOf(any<Repo>())<!> wasNot Called }
    verifyCount { 2.<!LOKKI_UNSUPPORTED!>times(check)<!> }
    verifyCount { 1 * <!LOKKI_UNSUPPORTED!>{ }<!> }

    <!LOKKI_UNSUPPORTED!>excludeRecords(excludeBlock = block)<!>
    excludeRecords <!LOKKI_UNSUPPORTED!>{ }<!>
    excludeRecords <!LOKKI_UNSUPPORTED!>{
        repo.find(1)
        nullable?.find(1)
    }<!>
    excludeRecords { <!LOKKI_UNSUPPORTED!>any<Repo>()<!>.find(1) }
    excludeRecords { repo.find(<!LOKKI_UNSUPPORTED!>any<Int>() + 1<!>) }
    val exclusion: (Boolean, MatcherScope.() -> Any?) -> Unit = <!LOKKI_UNSUPPORTED!>::excludeRecords<!>
}
