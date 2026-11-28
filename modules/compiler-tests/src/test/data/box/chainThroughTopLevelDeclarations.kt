// MODULE: main
// INTERCEPTABLE: sample.**
// FILE: services.kt
package sample

interface Service {
    fun ping(): String = "real"
}

var service: Service = object : Service {}

fun serviceFn(): Service = service

suspend fun coServiceFn(): Service = service

class Box

val Box.ext: Service
    get() = service

object Registry {
    var service: Service = object : Service {}
}

// MODULE: test()(main)
// FILE: test.kt
package sample

import io.lokki.Called
import io.lokki.coVerify
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.lokkiScope
import io.lokki.mock
import io.lokki.mockStatic
import io.lokki.verify

private inline fun fails(block: () -> Unit): Boolean =
    try {
        block()
        false
    } catch (_: AssertionError) {
        true
    }

fun box(): String {
    service = mock<Service>()
    every { service.ping() } returns "property"
    if (service.ping() != "property") return "Fail every through a property"
    verify { service.ping() }

    every { serviceFn().ping() } returns "function"
    if (serviceFn().ping() != "function") return "Fail every through a function"
    verify { serviceFn().ping() }

    val box = Box()
    every { box.ext.ping() } returns "extension"
    if (box.ext.ping() != "extension") return "Fail every through an extension property"
    verify { box.ext.ping() }

    service = mock<Service>(relaxed = true)
    excludeRecords { service.ping() }
    service.ping()
    confirmVerified(service)

    service = mock<Service>(relaxed = true)
    verify { service wasNot Called }
    coVerify { coServiceFn() wasNot Called }
    service.ping()
    if (!fails { verify { service wasNot Called } }) return "Fail wasNot Called on a used mock in a property"
    if (!fails { coVerify { coServiceFn() wasNot Called } }) return "Fail wasNot Called on a suspend function"

    Registry.service = mock<Service>(relaxed = true)
    verify { Registry.service wasNot Called }
    Registry.service.ping()
    if (!fails { verify { Registry.service wasNot Called } }) return "Fail wasNot Called on a member of an object"

    return lokkiScope {
        val stubbed = mock<Service>()
        mockStatic(::serviceFn)
        verify { serviceFn() wasNot Called }
        every { serviceFn() } returns stubbed
        every { serviceFn().ping() } returns "chained"
        if (serviceFn().ping() != "chained") return@lokkiScope "Fail chain through a mocked function"
        if (!fails { verify { serviceFn() wasNot Called } }) return@lokkiScope "Fail wasNot Called on a mocked function"
        "OK"
    }
}
