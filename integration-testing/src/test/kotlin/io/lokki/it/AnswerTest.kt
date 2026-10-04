package io.lokki.it

import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.mock
import io.lokki.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnswerTest {
    private val radio = mock<Radio>()
    private val dashboard = Dashboard(radio)

    @Test
    fun answersFollowOneAnother() {
        every { radio.tune(any(), any()) } returns "news" andThen "weather" andThenThrows IllegalStateException("off")

        assertEquals("news", dashboard.news())
        assertEquals("weather", dashboard.news())
        assertEquals("off", assertFailsWith<IllegalStateException> { dashboard.news() }.message)
    }

    @Test
    fun answerReadsArgumentsOfTheCall() {
        val station = slot<Int>()
        every { radio.tune(capture(station), any()) } answers { "station ${firstArg<Int>()} at ${secondArg<Int>()}" }

        assertEquals("station 101 at 5", dashboard.news())
        assertEquals("station 101 at 11", dashboard.loudNews())
        assertEquals(101, station.captured)
    }

    @Test
    fun helperOfTheTestGivesTheMatcher() {
        every { radio.tune(fm(), any()) } returns "fm"
        assertEquals("fm", dashboard.news())

        every { radio.tune(allAny()) } returns "any"
        assertEquals("any", dashboard.loudNews())
    }

    @Test
    fun mockCalledAtRegistrationKeepsItsAnswers() {
        val car = mock<Car>()
        every { car.drive(any()) } returnsMany listOf("101", "102")
        every { radio.tune(car.drive(1).toInt()) } returns "news"

        assertEquals("news", dashboard.news())
        assertEquals(listOf("101", "102"), List(2) { car.drive(1) })
    }

    private fun MatcherScope.fm(): Int = range(88, 108)
}
