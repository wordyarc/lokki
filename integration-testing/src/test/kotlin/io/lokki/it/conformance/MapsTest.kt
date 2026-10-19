package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapsTest {
    open class CloudBlockBlob {
        open var metadata: HashMap<String?, String?>? = null
    }

    @Test
    fun stackOverflowInHashMap() {
        val blob: CloudBlockBlob = mock()
        val metadata: HashMap<String?, String?> = hashMapOf()
        every { blob.metadata } returns metadata

        assertEquals(blob.metadata, metadata)
    }

    @Test
    fun hashmapMock() {
        val map: HashMap<String, String> = mock()
        every { map["abc"] } returns "def"
        every { map.put("ghi", "klm") } returns "nop"

        assertFailsWith<LokkiException> { map["def"] }
        assertEquals("def", map["abc"])
        assertFailsWith<LokkiException> { map["gh"] = "kl" }
        assertEquals("nop", map.put("ghi", "klm"))

        verify { map["abc"] }
        verify { map.put("ghi", "klm") }
    }
}
