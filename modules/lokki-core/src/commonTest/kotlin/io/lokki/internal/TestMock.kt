package io.lokki.internal

import io.lokki.MethodDescription

internal class TestMock(
    name: String?,
    vararg keys: String,
    flags: Int = 0,
    aliases: Array<String> = emptyArray(),
) : GeneratedMock {
    private val keys = ANY_KEYS + keys

    override val `lokki$state`: MockState =
        MockState(this, MockSpec(name, flags), MockDescriptor("Repo", this.keys, aliases))

    var original: (Int, Array<Any?>) -> Any? = `lokki$state`::noOriginal

    var relaxed: (Int) -> Any? = { NoRelaxedDefault("Any") }

    override fun `lokki$original`(member: Int, args: Array<Any?>): Any? = original(member, args)

    override fun `lokki$relaxed`(member: Int): Any? = relaxed(member)

    override fun `lokki$describe`(member: Int): MethodDescription? = keys.getOrNull(member)?.let(::descriptionOf)
}

private fun descriptionOf(key: String): MethodDescription {
    val parameters = key.lastIndexOf('(')
    val name = key.substring(key.lastIndexOfAny(charArrayOf('.', '#'), parameters) + 1, parameters)
    val types = key.substring(parameters + 1, key.length - 1).split(',').filter { it.isNotEmpty() }
        .map { TYPES[it] ?: Any::class }
    return MethodDescription(name, String::class, 0, TestMock::class, types, -1)
}

private val TYPES = mapOf("kotlin.Int" to Int::class, "kotlin.Boolean" to Boolean::class, "kotlin.String" to String::class)
