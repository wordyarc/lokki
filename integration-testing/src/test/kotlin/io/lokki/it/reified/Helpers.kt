package io.lokki.it.reified

import io.lokki.mock

interface Wheel {
    fun size(): Int
}

interface Crate<T> {
    fun item(): T
}

inline fun <reified T : Any> relaxedMock(): T = mock<T>(relaxed = true)

inline fun <reified T : Any> nestedHelper(): T = relaxedMock<T>()
