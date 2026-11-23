package sample

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.OverrideMocks
import io.lokki.isMock

interface Dep

class Target {
    var dep: Dep = object : Dep {}
    var other: Dep? = null
    var label: String = "keep"
}

class Inject {
    @Mock
    lateinit var dep: Dep

    @InjectMocks
    var keeps = Target()

    @InjectMocks(overrideValues = true)
    var replaces = Target()

    @OverrideMocks
    var overrides = Target()
}

fun box(): String {
    val test = Inject()
    MockAnnotations.init(test)
    if (isMock(test.keeps.dep)) return "Fail value replaced without overrideValues"
    if (test.keeps.other !== test.dep) return "Fail empty property not injected"
    if (test.replaces.dep !== test.dep) return "Fail overrideValues"
    if (test.overrides.dep !== test.dep) return "Fail OverrideMocks"
    return if (test.replaces.label == "keep") "OK" else "Fail unrelated property"
}
