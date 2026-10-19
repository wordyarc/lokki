// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock

interface Car

interface Box<T>

inline fun <reified T : Any> create(): T = mock<<!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>T<!>>()

inline fun <reified T : Any> inferred(): T = <!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>mock()<!>

inline fun <reified T : Car> named(name: String): T = mock<<!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>T<!>>(name) { }

inline fun <reified T> definitelyNotNull(): T & Any = mock<<!DEFINITELY_NON_NULLABLE_AS_REIFIED!>T & Any<!>>()

class Factory<T : Any> {
    inline fun <reified R : T> create(): R = mock<<!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>R<!>>()

    fun box(): Box<T> = mock<Box<T>>()
}

inline fun <reified T> box(): Box<T> = mock<Box<T>>()

fun test() {
    create<Car>()
    mock<Car>()
}
