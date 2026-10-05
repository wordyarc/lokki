// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// FILE: DoNotMock.kt
package com.google.errorprone.annotations

annotation class DoNotMock(val value: String = "Create a real instance instead")

// FILE: test.kt
package sample

import io.lokki.mock
import java.io.File
import java.nio.file.Path

annotation class DoNotMock

@com.google.errorprone.annotations.DoNotMock("Use FakeClock.")
interface Clock

interface SystemClock : Clock

@com.google.errorprone.annotations.DoNotMock
interface Ledger

@DoNotMock
abstract class Money

interface Names : Collection<String>

interface Car

typealias Location = Path

fun test() {
    mock<<!LOKKI_RESTRICTED_TYPE!>Clock<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>SystemClock<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>Ledger<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>Money<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>Names<!>>()
    <!LOKKI_UNSUPPORTED!>mock<<!LOKKI_RESTRICTED_TYPE!>List<String><!>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<<!LOKKI_RESTRICTED_TYPE!>MutableSet<Int><!>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<<!LOKKI_RESTRICTED_TYPE!>java.util.ArrayList<String><!>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<<!LOKKI_RESTRICTED_TYPE!>java.util.Queue<String><!>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<<!LOKKI_RESTRICTED_TYPE!>HashMap<String, Int><!>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<<!LOKKI_RESTRICTED_TYPE!>java.util.LinkedHashMap<String, Int><!>>()<!>
    mock<<!LOKKI_RESTRICTED_TYPE!>File<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>Path<!>>()
    mock<<!LOKKI_RESTRICTED_TYPE!>Location<!>>()
    val path: Path = <!LOKKI_RESTRICTED_TYPE!>mock()<!>

    mock<Car>()
    <!LOKKI_UNSUPPORTED!>mock<Map<String, Int>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Iterable<String>>()<!>
    mock<Runnable>()
}
