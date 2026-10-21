package io.lokki.internal

@PublishedApi
internal class MockSpec(val name: String?, val flags: Int)

internal const val RELAXED = 1
internal const val RELAX_UNIT_FUN = 2
internal const val SPY = 4
internal const val DELEGATE = 8
