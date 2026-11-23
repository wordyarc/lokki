// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// RESTRICTED: sample.Banned sample.banned.** sample.Forbidden

// FILE: sample.kt
package sample

annotation class Forbidden

interface Banned

interface BannedChild : Banned

@Forbidden
interface Marked

interface Allowed

// FILE: nested.kt
package sample.banned.deep

interface Nested

// FILE: test.kt
package test

import io.lokki.mock
import sample.Allowed
import sample.Banned
import sample.BannedChild
import sample.Marked
import sample.banned.deep.Nested

fun check() {
    mock<<!LOKKI_RESTRICTED_TYPE!>Banned<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>BannedChild<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>Marked<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>Nested<!>>()
    mock<Allowed>()
}
