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
            instance as? GeneratedMock ?: instance?.let(Redirects::find)
                ?: throw LokkiException("$instance is not a lokki mock")

        inline fun <T> withoutStateChanges(block: () -> T): T {
            Registration.enter()
            try {
                return block()
            } finally {
                Registration.exit()
            }
        }

        inline fun <T> chain(block: () -> T): T {
            Registration.enterChain()
            try {
                return block()
            } finally {
                Registration.exitChain()
            }
        }

        inline fun <T> link(
            receiver: Any?,
            key: String,
            stubs: Boolean,
            noinline factory: ((MockSpec) -> Any)?,
            problem: String?,
            block: () -> T,
        ): T {
            val link = Registration.enterLink(receiver, key, stubs, factory, problem)
            try {
                return block()
            } finally {
                Registration.exitLink(link)
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
internal class NoRelaxedDefault(internal val type: String, internal val mockable: Boolean)

@PublishedApi
internal class ChildMock(internal val factory: (MockSpec) -> Any)
