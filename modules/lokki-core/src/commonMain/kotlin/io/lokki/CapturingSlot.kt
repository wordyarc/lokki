package io.lokki

import io.lokki.internal.toStr
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

    override fun toString(): String = if (isCaptured) "slot(captured=${value.toStr()})" else "slot()"
}

public fun <T> slot(): CapturingSlot<T> = CapturingSlot()

public fun <R> CapturingSlot<() -> R>.invoke(): R = captured()

public fun <A1, R> CapturingSlot<(A1) -> R>.invoke(arg1: A1): R = captured(arg1)

public fun <A1, A2, R> CapturingSlot<(A1, A2) -> R>.invoke(arg1: A1, arg2: A2): R = captured(arg1, arg2)

public fun <
    A1, A2, A3, R,
> CapturingSlot<
    (
        A1, A2, A3,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3,
): R = captured(
    arg1, arg2, arg3,
)

public fun <
    A1, A2, A3, A4, R,
> CapturingSlot<
    (
        A1, A2, A3, A4,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4,
): R = captured(
    arg1, arg2, arg3, arg4,
)

public fun <
    A1, A2, A3, A4, A5, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5,
): R = captured(
    arg1, arg2, arg3, arg4, arg5,
)

public fun <
    A1, A2, A3, A4, A5, A6, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19, arg20,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20,
    arg21: A21,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19, arg20, arg21,
)

public fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, R,
> CapturingSlot<
    (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
    ) -> R,
>.invoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20,
    arg21: A21, arg22: A22,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19, arg20, arg21, arg22,
)

public suspend fun <R> CapturingSlot<suspend () -> R>.coInvoke(): R = captured()

public suspend fun <A1, R> CapturingSlot<suspend (A1) -> R>.coInvoke(arg1: A1): R = captured(arg1)

public suspend fun <
    A1, A2, R,
> CapturingSlot<
    suspend (
        A1, A2,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2,
): R = captured(
    arg1, arg2,
)

public suspend fun <
    A1, A2, A3, R,
> CapturingSlot<
    suspend (
        A1, A2, A3,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3,
): R = captured(
    arg1, arg2, arg3,
)

public suspend fun <
    A1, A2, A3, A4, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4,
): R = captured(
    arg1, arg2, arg3, arg4,
)

public suspend fun <
    A1, A2, A3, A4, A5, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5,
): R = captured(
    arg1, arg2, arg3, arg4, arg5,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19, arg20,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20,
    arg21: A21,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19, arg20, arg21,
)

public suspend fun <
    A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, R,
> CapturingSlot<
    suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
    ) -> R,
>.coInvoke(
    arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11,
    arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20,
    arg21: A21, arg22: A22,
): R = captured(
    arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18,
    arg19, arg20, arg21, arg22,
)

private object NotCaptured
