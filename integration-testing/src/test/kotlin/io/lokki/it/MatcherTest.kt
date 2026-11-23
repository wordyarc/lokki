package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import io.lokki.context.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatcherTest {
    @Test
    fun lastMatchingStubAnswersToMain() {
        val radio = mock<Radio>()
        every { radio.tune(any(), any()) } returns "noise"
        every { radio.tune(more(100), range(1, 10)) } returns "news"
        every { radio.tune(volume = eq(11), station = any()) } returns "loud news"
        val dashboard = Dashboard(radio)

        assertEquals("news", dashboard.news())
        assertEquals("loud news", dashboard.loudNews())
        assertEquals("noise", radio.tune(1, 0))
    }

    @Test
    fun matcherOfSubtypeDoesNotMatchAnotherSubtype() {
        val player = mock<Player>()
        every { player.play(any<String>(), more(5)) } returns "song"

        assertEquals("song", player.play("a", 6))
        assertFailsWith<LokkiException> { player.play(1, 6) }
        assertFailsWith<LokkiException> { player.play("a", 6L) }
    }

    @Test
    fun arrayInvokeAndContextMatchersReachMain() {
        val scheduler = mock<Scheduler>()
        every { scheduler.schedule(array(1, any()), invoke(5)) } returns 2
        every { withContext<Tenant> { scheduler.owner(any()) } } returns "owner"
        val planner = Planner(scheduler)
        val ran = mutableListOf<Int>()

        assertEquals(2, planner.plan(intArrayOf(1, 9)) { ran += it })
        assertEquals(listOf(5), ran)
        assertFailsWith<LokkiException> { planner.plan(intArrayOf(2, 9)) { ran += it } }
        assertEquals("owner", with(Tenant("first")) { planner.ownerOf(3) })
        verify { withContext<Tenant> { scheduler.owner(3) } }
    }
}
