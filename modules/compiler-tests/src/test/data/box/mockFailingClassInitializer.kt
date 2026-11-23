package sample

import io.lokki.LokkiException
import io.lokki.mock

open class Broken {
    companion object {
        init {
            throw IllegalStateException("simulated")
        }
    }
}

private fun failure(): Throwable? =
    try {
        mock<Broken>()
        null
    } catch (e: Throwable) {
        e
    }

fun box(): String {
    val first = failure() as? LokkiException ?: return "first failure is not a LokkiException"
    if (first.message != "a mock of sample.Broken cannot be created: its class failed to initialize") return first.message!!
    val initializer = first.cause as? ExceptionInInitializerError ?: return "cause: ${first.cause}"
    if (initializer.cause?.message != "simulated") return "root cause: ${initializer.cause}"
    val second = failure() as? LokkiException ?: return "second failure is not a LokkiException"
    if (second.cause !is LinkageError) return "second cause: ${second.cause}"
    return "OK"
}
