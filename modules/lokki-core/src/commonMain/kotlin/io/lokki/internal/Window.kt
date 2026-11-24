package io.lokki.internal

import io.lokki.LokkiScope

internal fun states(mocks: Array<out Any>): List<MockState> = mocks.map { GeneratedMock.of(it).`lokki$state`() }

internal class Window(val states: List<MockState>, val since: Long)

internal fun window(function: String, mocks: Array<out Any>): Window {
    if (mocks.isNotEmpty()) return Window(states(mocks), ALL)
    val scope = LokkiScope.requireCurrent(function)
    return Window(scope.windowStates(), scope.start)
}
