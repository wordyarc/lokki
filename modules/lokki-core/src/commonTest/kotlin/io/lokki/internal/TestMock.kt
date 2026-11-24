package io.lokki.internal

import io.lokki.MethodDescription
import io.lokki.RETURNS_UNIT
import io.lokki.SUSPEND

internal class TestMock(
    name: String?,
    vararg keys: String,
    flags: Int = 0,
    aliases: Array<String> = emptyArray(),
    private val suspending: Set<String> = emptySet(),
    private val units: Set<String> = emptySet(),
) : GeneratedMock {
    private val keys = ANY_KEYS + keys

    private val state = MockState(this, MockSpec(name, flags), MockDescriptor("Repo", LokkiAbi.VERSION, this.keys, aliases))

    var original: (Int, Array<Any?>) -> Any? = state::noOriginal

    var coOriginal: suspend (Int, Array<Any?>) -> Any? = state::noOriginal

    override fun `lokki$state`(): MockState = state

    var relaxed: (Int) -> Any? = { NoRelaxedDefault("Any", mockable = false) }

    override fun `lokki$original`(member: Int, args: Array<Any?>): Any? = original(member, args)

    override suspend fun `lokki$coOriginal`(member: Int, args: Array<Any?>): Any? = coOriginal(member, args)

    override fun `lokki$relaxed`(member: Int): Any? = relaxed(member)

    override fun `lokki$describe`(member: Int): MethodDescription? =
        keys.getOrNull(member)?.let { key ->
            descriptionOf(key, (if (key in suspending) SUSPEND else 0) or (if (key in units) RETURNS_UNIT else 0))
        }
}

private fun descriptionOf(key: String, flags: Int): MethodDescription {
    val parameters = key.lastIndexOf('(')
    val name = key.substring(key.lastIndexOfAny(charArrayOf('.', '#'), parameters) + 1, parameters)
    val types = key.substring(parameters + 1, key.length - 1).split(',').filter { it.isNotEmpty() }
        .map { TYPES[it] ?: Any::class }
    return MethodDescription(name, String::class, flags, TestMock::class, types, -1)
}

private val TYPES = mapOf("kotlin.Int" to Int::class, "kotlin.Boolean" to Boolean::class, "kotlin.String" to String::class)
