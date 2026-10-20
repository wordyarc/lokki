// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.verify

@JvmInline
value class Id(val value: Int)

@JvmInline
value class Name(val value: String)

interface Repo {
    fun find(id: Id): String
    fun named(name: Name?): String
    fun load(id: Int): String
    fun result(result: Result<String>): String
    fun any(value: Any): String
}

fun MatcherScope.sameId(id: Id): Id = <!LOKKI_REF_EQ_OF_VALUE_CLASS!>refEq<!>(id)

fun test(repo: Repo, id: Id, name: Name, text: String) {
    every { repo.find(<!LOKKI_REF_EQ_OF_VALUE_CLASS!>refEq<!>(id)) } returns "id"
    every { repo.find(<!LOKKI_REF_EQ_OF_VALUE_CLASS!>nrefEq<!>(id)) } returns "other"
    every { repo.named(<!LOKKI_REF_EQ_OF_VALUE_CLASS!>refEq<!>(name)) } returns "name"
    every { repo.result(<!LOKKI_REF_EQ_OF_VALUE_CLASS!>refEq<!>(Result.success(text))) } returns "result"
    every { repo.any(<!LOKKI_REF_EQ_OF_VALUE_CLASS!>refEq<!>(id)) } returns "any"
    verify { repo.find(<!LOKKI_REF_EQ_OF_VALUE_CLASS!>refEq<!>(id)) }

    every { repo.find(eq(id)) } returns "eq"
    every { repo.load(refEq(1)) } returns "int"
    every { repo.any(refEq(text)) } returns "text"
    every { repo.any(refEq<Any>(id)) } returns "boxed"
}
