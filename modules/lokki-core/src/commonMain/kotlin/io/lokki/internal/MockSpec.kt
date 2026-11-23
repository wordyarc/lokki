package io.lokki.internal

@PublishedApi
internal class MockSpec internal constructor(
    val name: String?,
    val flags: Int,
    internal val representative: MockState?,
) {
    constructor(name: String?, flags: Int) : this(name, flags, null)
}

internal const val RELAXED = LokkiAbi.FLAG_RELAXED
internal const val RELAX_UNIT_FUN = LokkiAbi.FLAG_RELAX_UNIT_FUN
internal const val SPY = LokkiAbi.FLAG_SPY
internal const val DELEGATE = LokkiAbi.FLAG_DELEGATE
internal const val OBJECT = LokkiAbi.FLAG_OBJECT
internal const val STATIC = LokkiAbi.FLAG_STATIC
internal const val CONSTRUCTOR = LokkiAbi.FLAG_CONSTRUCTOR
internal const val RECORD_PRIVATE_CALLS = LokkiAbi.FLAG_RECORD_PRIVATE_CALLS
