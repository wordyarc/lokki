package io.lokki

import kotlin.concurrent.Volatile

public class CapturingSlot<T> {
    @Volatile
    private var value: Any? = NotCaptured

    public val isCaptured: Boolean
        get() = value !== NotCaptured

    public val isNull: Boolean
        get() = value == null

    public var captured: T
        get() {
            val value = value
            check(value !== NotCaptured) { "Value not yet captured." }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        set(value) {
            this.value = value
        }

    public fun clear() {
        value = NotCaptured
    }

    override fun toString(): String = if (isCaptured) "slot(captured=$value)" else "slot()"
}

public fun <T> slot(): CapturingSlot<T> = CapturingSlot()

private object NotCaptured
