// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.InjectMocks

class NodeX(val y: NodeY)

class NodeY(val x: NodeX)

class CircularTestTarget {
    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var x: NodeX

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var y: NodeY
}
