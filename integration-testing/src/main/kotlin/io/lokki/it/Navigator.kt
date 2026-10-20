package io.lokki.it

interface Navigator {
    suspend fun route(to: String): List<String>
    suspend fun reset()
}

class Trip(private val navigator: Navigator) {
    suspend fun plan(to: String): String {
        navigator.reset()
        return navigator.route(to).joinToString(" -> ")
    }
}
