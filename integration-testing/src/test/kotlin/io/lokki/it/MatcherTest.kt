package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
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
}
