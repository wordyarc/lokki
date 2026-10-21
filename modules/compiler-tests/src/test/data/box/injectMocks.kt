package app

import io.lokki.InjectMocks
import io.lokki.InjectionLookupType
import io.lokki.LokkiException
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.Spy
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.isMock
import io.lokki.verify

interface Repo {
    fun find(id: Int): String
}

interface Clock {
    fun now(): Long
}

interface Listener

open class Service(val repo: Repo, val clock: Clock, val timeout: Int = 30) {
    constructor(repo: Repo) : this(repo, FixedClock, 1)

    open fun describe(id: Int): String = "${repo.find(id)} at ${clock.now()}"
}

object FixedClock : Clock {
    override fun now(): Long = 0
}

object OtherClock : Clock {
    override fun now(): Long = 1
}

class Listeners(val all: List<Listener>)

class Box<T>(val item: T)

abstract class Base<T : Any> {
    lateinit var dependency: T

    var optional: Clock? = null
}

class Derived : Base<Repo>() {
    var note: String? = null

    lateinit var configured: Clock
}

class Chain(val service: Service, val listeners: Listeners)

open class Notifier {
    open var clock: Clock? = null

    open fun notifyAt(): Long = clock!!.now()
}

class ServiceFixture {
    @Mock
    lateinit var repo: Repo

    @Mock
    lateinit var clock: Clock

    @Mock
    lateinit var first: Listener

    @Mock
    lateinit var second: Listener

    @InjectMocks
    lateinit var chain: Chain

    @InjectMocks
    lateinit var service: Service

    @InjectMocks
    lateinit var listeners: Listeners

    @InjectMocks
    var derived: Derived? = null

    @InjectMocks
    lateinit var box: Box<Repo>
}

class Preset {
    @Mock
    lateinit var repo: Repo

    val clock: Clock = FixedClock

    var item: Listener? = null

    @InjectMocks
    var derived = Derived().apply { configured = OtherClock }
}

class ByName {
    @Mock
    lateinit var repo: Repo

    @Mock
    lateinit var timer: Clock

    @InjectMocks(lookupType = InjectionLookupType.BY_NAME)
    lateinit var service: Service
}

class ByType {
    @Mock
    lateinit var storage: Repo

    @InjectMocks(lookupType = InjectionLookupType.BY_TYPE)
    lateinit var service: Service
}

class Spied {
    @Mock
    lateinit var repo: Repo

    @Mock
    lateinit var clock: Clock

    @InjectMocks
    @Spy
    lateinit var service: Service

    @InjectMocks
    @Spy
    lateinit var notifier: Notifier
}

open class BaseFixture {
    @Mock
    lateinit var repo: Repo
}

class InheritedFixture : BaseFixture() {
    val clock: Clock = FixedClock

    @InjectMocks
    lateinit var service: Service
}

class Missing {
    lateinit var repo: Repo

    var clock: Clock = FixedClock

    @InjectMocks
    lateinit var service: Service
}

fun box(): String {
    val fixture = ServiceFixture()
    MockAnnotations.init(fixture)
    val service = fixture.service
    if (service.repo !== fixture.repo || service.clock !== fixture.clock) return "Fail: constructor by name"
    if (service.timeout != 30) return "Fail: default ${service.timeout}"
    if (fixture.chain.service !== service || fixture.chain.listeners !== fixture.listeners) {
        return "Fail: order of @InjectMocks"
    }
    if (fixture.listeners.all != listOf(fixture.first, fixture.second)) return "Fail: list ${fixture.listeners.all}"
    if (fixture.box.item !== fixture.repo) return "Fail: generic constructor"
    val derived = fixture.derived!!
    if (derived.dependency !== fixture.repo) return "Fail: inherited generic property"
    if (derived.optional !== fixture.clock || derived.configured !== fixture.clock) return "Fail: properties"
    if (derived.note != null) return "Fail: property without a source"
    every { fixture.repo.find(1) } returns "one"
    every { fixture.clock.now() } returns 5
    if (service.describe(1) != "one at 5") return "Fail: describe ${service.describe(1)}"

    MockAnnotations.init(fixture)
    if (fixture.service !== service || fixture.derived !== derived) return "Fail: repeated init"

    val preset = Preset()
    val presetDerived = preset.derived
    MockAnnotations.init(preset)
    if (preset.derived !== presetDerived) return "Fail: preset object is replaced"
    if (presetDerived.dependency !== preset.repo) return "Fail: preset lateinit"
    if (presetDerived.configured !== OtherClock || presetDerived.optional !== FixedClock) {
        return "Fail: preset properties"
    }

    val byName = ByName()
    MockAnnotations.init(byName)
    if (byName.service.repo !== byName.repo || byName.service.clock !== FixedClock) return "Fail: by name"

    val byType = ByType()
    MockAnnotations.init(byType)
    if (byType.service.repo !== byType.storage || byType.service.timeout != 1) return "Fail: by type"

    val spied = Spied()
    MockAnnotations.init(spied)
    if (!isMock(spied.service) || spied.service.repo !== spied.repo) return "Fail: spy"
    every { spied.repo.find(2) } returns "two"
    every { spied.clock.now() } returns 7
    if (spied.service.describe(2) != "two at 7") return "Fail: spy original ${spied.service.describe(2)}"
    verify { spied.service.describe(2) }
    confirmVerified(spied.notifier)
    if (spied.notifier.clock !== spied.clock || spied.notifier.notifyAt() != 7L) return "Fail: properties of a spy"

    val inherited = InheritedFixture()
    MockAnnotations.init(inherited)
    if (inherited.service.repo !== inherited.repo || inherited.service.clock !== FixedClock) return "Fail: superclass"

    val missing = Missing()
    val message = try {
        MockAnnotations.init(missing)
        "no exception"
    } catch (e: LokkiException) {
        e.message
    }
    if (message != "@InjectMocks: property 'repo' for parameter 'repo' of 'app.Service' is null or not initialized") {
        return "Fail: missing $message"
    }
    return "OK"
}
