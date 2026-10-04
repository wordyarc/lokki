// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.CapturingSlot
import io.lokki.MatcherScope
import io.lokki.StubScope
import io.lokki.every
import io.lokki.isMock
import io.lokki.slot

fun test() {
    val reference: (MatcherScope.() -> String) -> StubScope<String> = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::every<!>
    val adapted: (MatcherScope.() -> String) -> Unit = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::every<!>
    val matcher: MatcherScope.(Int) -> Int = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>MatcherScope::neq<!>
    val regular: (Any) -> Boolean = ::isMock
    val generic: () -> CapturingSlot<Int> = ::slot
}

fun MatcherScope.helper(): () -> String = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::isNull<!>
