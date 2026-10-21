// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.InjectMocks
import io.lokki.InjectionLookupType
import io.lokki.Mock
import io.lokki.Spy

interface Repo

interface Clock

interface Listener

class Service(val repo: Repo, val clock: Clock)

class Listened(val listener: Listener)

class Twins(val repo: Repo) {
    constructor(clock: Clock) : this(object : Repo {})
}

class Hidden private constructor(val repo: Repo)

abstract class Abstract(val repo: Repo)

class Outer {
    inner class Inner(val repo: Repo)
}

class First(val second: Second)

class Second(val first: First)

class Settable {
    var listener: Listener? = null
}

class Guarded {
    private var repo: Repo? = null

    var clock: Clock? = null
        private set
}

class Parts(val repo: Repo) {
    lateinit var clock: Clock
}

open class Open(val repo: Repo)

class Final(val repo: Repo)

class Fixture<T : Any> {
    @Mock
    lateinit var repo: Repo

    @Mock
    lateinit var clock: Clock

    @Mock
    lateinit var first: Listener

    @Mock
    lateinit var second: Listener

    @InjectMocks
    lateinit var service: Service

    @InjectMocks
    lateinit var parts: Parts

    <!LOKKI_AMBIGUOUS_INJECTION!>@InjectMocks<!>
    lateinit var listened: Listened

    <!LOKKI_AMBIGUOUS_INJECTION!>@InjectMocks<!>
    lateinit var twins: Twins

    <!LOKKI_AMBIGUOUS_INJECTION!>@InjectMocks<!>
    lateinit var settable: Settable

    <!LOKKI_UNSUPPORTED_INJECTION!>@InjectMocks<!>
    lateinit var guarded: Guarded

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var hidden: Hidden

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var repository: Repo

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var abstract: Abstract

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var inner: Outer.Inner

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var parameter: T

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var cycleFirst: First

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks<!>
    lateinit var cycleSecond: Second

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

class Lookup {
    @Mock
    lateinit var storage: Repo

    @Mock
    lateinit var clock: Clock

    <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks(lookupType = InjectionLookupType.BY_NAME)<!>
    lateinit var byName: Service

    @InjectMocks(lookupType = InjectionLookupType.BY_TYPE)
    lateinit var byType: Service
}

class Parameters(<!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@InjectMocks<!> val service: Service)
