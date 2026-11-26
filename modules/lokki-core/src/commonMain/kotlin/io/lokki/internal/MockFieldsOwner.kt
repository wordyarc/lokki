package io.lokki.internal

@PublishedApi
internal interface MockFieldsOwner {
    @JvmSynthetic
    fun `lokki$initMockFields`(flags: Int)
}

internal expect fun uninitializedMockPropertiesOwner(target: Any): String?
