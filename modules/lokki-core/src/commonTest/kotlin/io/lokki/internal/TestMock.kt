package io.lokki.internal

internal class TestMock(name: String?, vararg keys: String) : GeneratedMock {
    override val `lokki$state`: MockState = MockState(this, MockSpec(name, 0), MockDescriptor("Repo", ANY_KEYS + keys))

    var original: (Int, Array<Any?>) -> Any? = `lokki$state`::noOriginal

    override fun `lokki$original`(member: Int, args: Array<Any?>): Any? = original(member, args)
}
