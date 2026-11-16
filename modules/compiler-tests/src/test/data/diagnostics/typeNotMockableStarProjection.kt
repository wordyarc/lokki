// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock

interface Car

interface Node<T : Node<T>>

interface Both<T> where T : CharSequence, T : Comparable<T>

interface Pairs<A, B : List<A>>

interface Ranged<T : Number>

fun test() {
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<Node<*>>()<!>
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<Both<*>>()<!>
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<Pairs<*, *>>()<!>
    mock<Car>(moreInterfaces = arrayOf(<!LOKKI_TYPE_NOT_MOCKABLE!>Node::class<!>))

    mock<Ranged<*>>()
    mock<Pairs<String, *>>()
}
