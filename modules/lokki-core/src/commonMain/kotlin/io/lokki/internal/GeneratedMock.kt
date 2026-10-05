package io.lokki.internal

import io.lokki.LokkiException

@PublishedApi
internal interface GeneratedMock {
    @get:JvmSynthetic
    val `lokki$state`: MockState

    @JvmSynthetic
    fun `lokki$original`(member: Int, args: Array<Any?>): Any?

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

internal fun states(function: String, mocks: Array<out Any>): List<MockState> {
    if (mocks.isEmpty()) {
        throw LokkiException("$function() without mocks is not supported yet: pass the mocks explicitly")
    }
    return mocks.map { GeneratedMock.of(it).`lokki$state` }
}
