// FILE: LokkiIntrinsic.kt
package io.lokki.internal

annotation class LokkiIntrinsic(val kind: Kind, val since: Int = 0) {
    enum class Kind {
        MOCK,
        SPY,
        RELAXED_MOCK,
        ADDITIONAL_INTERFACE,
        INJECT_MOCKS,
        STUB_BLOCK,
        JUST_RUN_BLOCK,
        JUST_AWAIT_BLOCK,
        VERIFY_BLOCK,
        VERIFY_ALL_BLOCK,
        VERIFY_ORDER_BLOCK,
        VERIFY_SEQUENCE_BLOCK,
        EXCLUDE_BLOCK,
        WAS_NOT_CALLED,
        WITH_CONTEXT,
        MATCHER,
        ANSWER,
        AWAITS,
        MOCK_OBJECT,
        MOCK_STATIC,
        UNMOCK_STATIC,
        CLEAR_STATIC_MOCK,
        CONFIRM_VERIFIED_STATIC,
    }
}

// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mock
import io.lokki.verify

interface Car {
    fun drive(speed: Int): Int
}

fun box(): String {
    val car = mock<Car>()
    every { car.drive(any()) } returns 5
    if (car.drive(1) != 5) return "Fail: stub"
    verify { car.drive(1) }
    return "OK"
}
