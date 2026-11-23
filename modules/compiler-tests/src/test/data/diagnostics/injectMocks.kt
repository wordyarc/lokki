// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.Spy

interface Repo

interface Clock

class Service(val repo: Repo, val clock: Clock)

open class Open(val repo: Repo)

class Final(val repo: Repo)

class Fixture {
    @Mock
    lateinit var repo: Repo

    @InjectMocks
    @Spy
    lateinit var open: Open

    @InjectMocks
    <!LOKKI_TYPE_NOT_MOCKABLE!>@Spy<!>
    lateinit var final: Final

    @Mock
    <!LOKKI_UNSUPPORTED_INJECTION!>@InjectMocks<!>
    lateinit var both: Service

    <!LOKKI_MOCK_PROPERTY_NOT_MUTABLE!>@InjectMocks<!>
    val readonly: Service? = null
}

class Parameters(@InjectMocks val service: Service)
