// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every

class Tenant

interface Repo {
    var limit: Any
    val size: Any
    fun find(id: Int, limit: Int = 10): Any
    fun count(): Any
    fun names(vararg names: String): Any
    fun String.width(padding: Int): Any

    context(tenant: Tenant)
    fun load(id: Int): Any
}

fun test(repo: Repo, tenant: Tenant, index: Int) {
    every { repo.find(1) } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>2<!>
    every { repo.find(1, 2) } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>-1<!>
    every { repo.find(1) }.returnsArgument(n = <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>2<!>)
    every { repo.count() } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>0<!>
    every { repo.size } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>0<!>
    every { repo.limit } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>0<!>
    every { repo.limit = 1 } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>1<!>
    every { repo.names("a", "b") } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>1<!>
    with(repo) { every { "a".width(1) } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>2<!> }
    with(tenant) { every { repo.load(1) } returnsArgument <!LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE!>2<!> }

    every { repo.find(1) } returnsArgument 0
    every { repo.find(1) } returnsArgument 1
    every { repo.find(1) }.returnsArgument(1)
    every { repo.limit = 1 } returnsArgument 0
    every { repo.names("a", "b") } returnsArgument 0
    with(repo) { every { "a".width(1) } returnsArgument 1 }
    with(tenant) { every { repo.load(1) } returnsArgument 1 }
    every { repo.find(1) } returnsArgument index
    val stub = every { repo.find(1) }
    stub returnsArgument 2
}
