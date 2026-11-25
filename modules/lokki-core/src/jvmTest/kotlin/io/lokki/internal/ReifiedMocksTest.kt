package io.lokki.internal

import io.lokki.LokkiException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReifiedMocksTest {
    private val spec = MockSpec(null, 0)

    @Test
    fun testCallCreatesMockWithItsOwnFactory() {
        val type = typeOf<Nested>()
        val outer = enter(type, "outer")
        val inner = enter(type, "inner")

        assertEquals("inner", create(type))
        ReifiedMocks.exit(inner)
        assertEquals("outer", create(type))
        ReifiedMocks.exit(outer)
    }

    @Test
    fun testCallTakesFactoryOfEnclosingCallForAnotherType() {
        val outer = enter(typeOf<Enclosing>(), "outer")
        val inner = enter(typeOf<Enclosed>(), "inner")

        assertEquals("outer", create(typeOf<Enclosing>()))
        ReifiedMocks.exit(inner)
        ReifiedMocks.exit(outer)
    }

    @Test
    fun testMockCreatedAfterCallsTakesFactoryOfLastCall() {
        val type = typeOf<Deferred>()
        ReifiedMocks.exit(enter(type, "first"))
        ReifiedMocks.exit(enter(type, "last"))

        assertEquals("last", create(type))
    }

    @Test
    fun testCallOfAnotherThreadDoesNotReplaceFactoryOfCurrentCall() {
        val type = typeOf<Concurrent>()
        val call = enter(type, "current")
        val entered = CountDownLatch(1)
        val created = CountDownLatch(1)
        var other: Any? = null

        val concurrent = thread {
            val concurrent = enter(type, "other")
            other = create(type)
            entered.countDown()
            created.await()
            ReifiedMocks.exit(concurrent)
        }
        entered.await()
        val current = create(type)
        created.countDown()
        concurrent.join()

        assertEquals("other", other)
        assertEquals("current", current)
        ReifiedMocks.exit(call)
    }

    @Test
    fun testCallIsExitedFromAnotherThread() {
        val type = typeOf<Resumed>()
        val call = enter(type, "suspended")

        thread { ReifiedMocks.exit(call) }.join()
        ReifiedMocks.register(type) { "registered" }

        assertEquals("registered", create(type))
    }

    @Test
    fun testTypeWithoutFactoryIsRejected() {
        val failure = assertFailsWith<LokkiException> { create(typeOf<Unregistered>()) }

        assertEquals(
            "mock<${typeOf<Unregistered>()}>() in an inline helper has no generated class: " +
                "apply the lokki plugin to the module that calls the helper",
            failure.message,
        )
    }

    private fun enter(type: KType, mock: String): ReifiedCall = ReifiedMocks.enter(arrayOf(type), arrayOf({ mock }))

    private fun create(type: KType): Any = ReifiedMocks.create(type, spec)

    private interface Nested

    private interface Enclosing

    private interface Enclosed

    private interface Deferred

    private interface Concurrent

    private interface Resumed

    private interface Unregistered
}
