package io.lokki.internal

@PublishedApi
internal class MockSpec(val name: String?, val flags: Int)

internal const val RELAXED = LokkiAbi.FLAG_RELAXED
internal const val RELAX_UNIT_FUN = LokkiAbi.FLAG_RELAX_UNIT_FUN
internal const val SPY = LokkiAbi.FLAG_SPY
internal const val DELEGATE = LokkiAbi.FLAG_DELEGATE
internal const val OBJECT = LokkiAbi.FLAG_OBJECT
internal const val STATIC = LokkiAbi.FLAG_STATIC
