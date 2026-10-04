// RUN_PIPELINE_TILL: FRONTEND
// WARNING_LEVEL: LOKKI_RESTRICTED_TYPE:disabled

package sample

import io.lokki.mock
import java.nio.file.Path

fun test() {
    mock<Path>()
}
