package io.lokki.it.mockable

class Odometer(private val start: Int) {
    fun read(): Int = start

    fun trip(previous: Int): Int = read() - previous
}
