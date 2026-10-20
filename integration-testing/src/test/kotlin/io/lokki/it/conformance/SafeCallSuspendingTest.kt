@file:Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER", "UNNECESSARY_SAFE_CALL", "RedundantSuspendModifier")

package io.lokki.it.conformance

import io.lokki.mock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class SafeCallSuspendingTest {
    open class MyClass {
        open suspend fun doSomething() {
        }
    }

    @Test
    fun testSafeCall() {
        runBlocking {
            var myPossiblyNullInstance: MyClass? = null
            myPossiblyNullInstance = mock(relaxed = true)
            myPossiblyNullInstance?.doSomething()
        }
    }
}
