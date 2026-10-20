package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.MethodDescription
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

@PublishedApi
internal interface GeneratedMock {
    @get:JvmSynthetic
    val `lokki$state`: MockState

    @JvmSynthetic
    fun `lokki$original`(member: Int, args: Array<Any?>): Any?

    @JvmSynthetic
    suspend fun `lokki$coOriginal`(member: Int, args: Array<Any?>): Any?

    @JvmSynthetic
    fun `lokki$relaxed`(member: Int): Any?

    @JvmSynthetic
    fun `lokki$describe`(member: Int): MethodDescription?

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

        fun <T> evaluate(dsl: String, block: suspend () -> T): T {
            val result = block.startCoroutineUninterceptedOrReturn(Abandoned)
            if (result === COROUTINE_SUSPENDED) {
                throw LokkiException("a receiver or an argument of $dsl suspended; evaluate it outside the block")
            }
            @Suppress("UNCHECKED_CAST")
            return result as T
        }
    }
}

@PublishedApi
internal class NoRelaxedDefault(internal val type: String)

internal fun states(function: String, mocks: Array<out Any>): List<MockState> {
    if (mocks.isEmpty()) {
        throw LokkiException("$function() without mocks is not supported yet: pass the mocks explicitly")
    }
    return mocks.map { GeneratedMock.of(it).`lokki$state` }
}
