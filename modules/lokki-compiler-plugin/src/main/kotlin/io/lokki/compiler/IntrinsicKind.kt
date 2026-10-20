package io.lokki.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal enum class IntrinsicKind {
    MOCK,
    STUB_BLOCK,
    MATCHER,
    ANSWER,
    VERIFY_BLOCK,
    WAS_NOT_CALLED,
    EXCLUDE_BLOCK,
    AWAITS,
    UNKNOWN;

    companion object {
        fun of(name: String): IntrinsicKind = entries.find { it.name == name } ?: UNKNOWN
    }
}

internal val LOKKI_INTRINSIC = ClassId(FqName("io.lokki.internal"), Name.identifier("LokkiIntrinsic"))

internal const val ALL_ANY = "allAny"
internal val COMPOSITE_MATCHERS = setOf("and", "or", "not")
internal val CAPTURE_MATCHERS = setOf("capture", "captureNullable")
internal val WITH_ARG_MATCHERS = setOf("withArg", "withNullableArg", "coWithArg", "coWithNullableArg")
internal val REF_EQ_MATCHERS = setOf("refEq", "nrefEq")
internal val JUST_RUN_BLOCKS = setOf("justRun", "coJustRun")
internal const val JUST_AWAIT_BLOCK = "coJustAwait"

internal val AWAIT_CANCELLATION = CallableId(FqName("kotlinx.coroutines"), Name.identifier("awaitCancellation"))
