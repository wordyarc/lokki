package io.lokki.internal

internal class TestMock(name: String?, vararg keys: String) : GeneratedMock {
    override val `lokki$state`: MockState = MockState(this, MockSpec(name, 0), MockDescriptor("Repo", ANY_KEYS + keys))
}
