package io.lokki.internal

import io.lokki.LokkiException

@PublishedApi
internal interface GeneratedMock {
    val `lokki$state`: MockState

    companion object {
        fun of(instance: Any?): GeneratedMock =
            instance as? GeneratedMock ?: throw LokkiException("$instance is not a lokki mock")
    }
}
