package io.lokki.it.mockable

object Clock {
    fun now(): Long = System.currentTimeMillis()
}

class Ticket(val issued: Long) {
    companion object {
        var prefix: String = "T"

        fun issue(): Ticket = Ticket(Clock.now())
    }

    fun code(): String = "$prefix$issued"
}
