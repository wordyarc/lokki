// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.CallCountVerificationScope
import io.lokki.CapturingSlot
import io.lokki.MatcherScope
import io.lokki.Ordering
import io.lokki.StubScope
import io.lokki.VerificationScope
import io.lokki.coExcludeRecords
import io.lokki.coVerify
import io.lokki.coVerifySequence
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.isMock
import io.lokki.slot
import io.lokki.verify
import io.lokki.verifyCount
import io.lokki.verifyOrder

fun test() {
    val reference: (MatcherScope.() -> String) -> StubScope<String, String> = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::every<!>
    val adapted: (MatcherScope.() -> String) -> Unit = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::every<!>
    val verification: (Ordering, Boolean, Int, Int, Int, Long, VerificationScope.() -> Any?) -> Unit =
        <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::verify<!>
    val order: (Boolean, VerificationScope.() -> Any?) -> Unit = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::verifyOrder<!>
    val matcher: MatcherScope.(Int) -> Int = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>MatcherScope::neq<!>
    val exclusion: (Boolean, MatcherScope.() -> Any?) -> Unit = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::excludeRecords<!>
    val count: (CallCountVerificationScope.() -> Unit) -> Unit = ::verifyCount
    val coVerification: (Ordering, Boolean, Int, Int, Int, Long, suspend VerificationScope.() -> Any?) -> Unit =
        <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::coVerify<!>
    val coSequence: (Boolean, suspend VerificationScope.() -> Any?) -> Unit =
        <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::coVerifySequence<!>
    val coExclusion: (Boolean, suspend MatcherScope.() -> Any?) -> Unit =
        <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::coExcludeRecords<!>
    val regular: (Any) -> Boolean = ::isMock
    val generic: () -> CapturingSlot<Int> = ::slot
    val answer = StubScope<String, String>::returnsArgument
}

fun MatcherScope.helper(): () -> String = <!LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC!>::isNull<!>
