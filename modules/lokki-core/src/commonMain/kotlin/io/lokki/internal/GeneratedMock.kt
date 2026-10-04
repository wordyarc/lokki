package io.lokki.internal

import io.lokki.LokkiException

@PublishedApi
internal interface GeneratedMock {
    @get:JvmSynthetic
    val `lokki$state`: MockState

    companion object {
        fun of(instance: Any?): GeneratedMock =
            instance as? GeneratedMock ?: throw LokkiException("$instance is not a lokki mock")

        inline fun <T> withoutStateChanges(block: () -> T): T {
            Registration.enter()
            try {
                return block()
            } finally {
                Registration.exit()
            }
        }
    }
}
