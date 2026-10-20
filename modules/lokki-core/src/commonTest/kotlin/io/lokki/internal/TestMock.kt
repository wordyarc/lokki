package io.lokki.internal

internal class TestMock(name: String?, vararg keys: String, flags: Int = 0) : GeneratedMock {
    override val `lokki$state`: MockState =
        MockState(this, MockSpec(name, flags), MockDescriptor("Repo", ANY_KEYS + keys))

    var original: (Int, Array<Any?>) -> Any? = `lokki$state`::noOriginal

    var relaxed: (Int) -> Any? = { NoRelaxedDefault("Any") }

    override fun `lokki$original`(member: Int, args: Array<Any?>): Any? = original(member, args)

    override fun `lokki$relaxed`(member: Int): Any? = relaxed(member)
}
