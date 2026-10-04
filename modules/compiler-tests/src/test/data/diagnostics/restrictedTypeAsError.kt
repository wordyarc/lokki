// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// WARNING_LEVEL: LOKKI_RESTRICTED_TYPE:error

package sample

import io.lokki.mock
import java.nio.file.Path

fun test() {
    mock<<!LOKKI_RESTRICTED_TYPE!>Path<!>>()
}
