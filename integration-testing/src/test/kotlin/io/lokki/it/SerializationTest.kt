package io.lokki.it

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import io.lokki.spy
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {
    @Test
    fun jacksonSeesOnlyPropertiesOfTheSpiedClass() {
        val json = ObjectMapper().writeValueAsString(spy(Ticket("1")))

        assertEquals(mapOf("id" to "1", "title" to "t-1"), ObjectMapper().readValue(json, Map::class.java))
    }

    @Test
    fun gsonSeesOnlyFieldsOfTheSpiedClass() {
        assertEquals("""{"id":"1"}""", Gson().toJson(spy(Ticket("1"))))
    }
}

open class Ticket(val id: String) {
    open val title: String
        get() = "t-$id"
}
