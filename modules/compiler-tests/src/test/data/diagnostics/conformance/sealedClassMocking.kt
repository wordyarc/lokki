// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.mock

private sealed class Base {
    abstract val i: Int
}

private data class Derived(
    override val i: Int,
) : Base()

fun testSealedClassCanBeMocked() {
    val base =
        mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Base<!>> {
            every { i } returns 0
        }
}
