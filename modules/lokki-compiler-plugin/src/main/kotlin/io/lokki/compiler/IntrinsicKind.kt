package io.lokki.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal enum class IntrinsicKind {
    MOCK,
    STUB_BLOCK,
    MATCHER,
    ANSWER,
    VERIFY_BLOCK,
    UNKNOWN;

    companion object {
        fun of(name: String): IntrinsicKind = entries.find { it.name == name } ?: UNKNOWN
    }
}

internal val LOKKI_INTRINSIC = ClassId(FqName("io.lokki.internal"), Name.identifier("LokkiIntrinsic"))

internal const val ALL_ANY = "allAny"
internal val COMPOSITE_MATCHERS = setOf("and", "or", "not")
