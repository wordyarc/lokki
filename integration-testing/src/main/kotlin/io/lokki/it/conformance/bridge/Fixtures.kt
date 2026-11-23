package io.lokki.it.conformance.bridge

interface NumberSource {
    val number: Number
}

abstract class IntSource(
    final override val number: Int,
) : NumberSource

class FinalIntSource(
    number: Int,
) : IntSource(number)

interface Source<T> {
    val value: T
}

open class StringSource(
    final override val value: String,
) : Source<String>

interface Sink<T> {
    fun accept(item: T): Boolean
}

class StringSink : Sink<String> {
    override fun accept(item: String): Boolean = false
}

class Square : (Int) -> Int {
    override fun invoke(value: Int): Int = value * value
}
