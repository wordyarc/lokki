// RUN_PIPELINE_TILL: BACKEND

// FILE: first.kt
package pAa

interface Car

// FILE: second.kt
package pBB

interface Car

// FILE: test.kt
import io.lokki.mock

val first = <!DUPLICATE_CLASS_NAMES!>mock<pAa.Car>()<!>
val second = <!DUPLICATE_CLASS_NAMES!>mock<pBB.Car>()<!>
