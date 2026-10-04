// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.CallCountVerificationScope
import io.lokki.CapturingSlot
import io.lokki.MatcherScope
import io.lokki.StubScope
import io.lokki.VerificationScope
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.isMock
import io.lokki.slot
import io.lokki.verify
import io.lokki.verifyCount

fun test() {
    val reference: (MatcherScope.() -> String) -> StubScope<String> = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::every<!>
    val adapted: (MatcherScope.() -> String) -> Unit = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::every<!>
    val verification: (Boolean, Int, Int, Int, VerificationScope.() -> Any?) -> Unit =
        <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::verify<!>
    val matcher: MatcherScope.(Int) -> Int = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>MatcherScope::neq<!>
    val exclusion: (Boolean, MatcherScope.() -> Any?) -> Unit = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::excludeRecords<!>
    val count: (CallCountVerificationScope.() -> Unit) -> Unit = ::verifyCount
    val regular: (Any) -> Boolean = ::isMock
    val generic: () -> CapturingSlot<Int> = ::slot
    val answer = StubScope<String>::returnsArgument
}

fun MatcherScope.helper(): () -> String = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::isNull<!>
