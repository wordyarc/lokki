// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock

interface Car

interface Box<T>

inline fun <reified T : Any> create(): T = mock<T>()

inline fun <reified T : Any> inferred(): T = mock()

inline fun <reified T : Car> named(name: String): T = mock<T>(name) { }

inline fun <reified T> definitelyNotNull(): T & Any = mock<<!DEFINITELY_NON_NULLABLE_AS_REIFIED!>T & Any<!>>()

class Factory<T : Any> {
    inline fun <reified R : T> create(): R = mock<R>()

    fun box(): Box<T> = mock<Box<T>>()
}

inline fun <reified T> box(): Box<T> = mock<Box<T>>()

fun test() {
    create<Car>()
    mock<Car>()
}
