package io.lokki.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val LOKKI_PACKAGE = FqName("io.lokki")
internal val LOKKI_INTERNAL_PACKAGE = FqName("io.lokki.internal")

internal fun publicClassId(name: String): ClassId = ClassId(LOKKI_PACKAGE, Name.identifier(name))

internal fun internalClassId(name: String): ClassId = ClassId(LOKKI_INTERNAL_PACKAGE, Name.identifier(name))

internal val LOKKI_INTRINSIC = internalClassId("LokkiIntrinsic")
internal val LOKKI_ABI = internalClassId("LokkiAbi")
internal val CHAINED_MOCKS = publicClassId("ChainedMocks")
internal val CAPTURING_SLOT = publicClassId("CapturingSlot")
internal val ORDERING = publicClassId("Ordering")
internal val AWAIT_CANCELLATION = CallableId(FqName("kotlinx.coroutines"), Name.identifier("awaitCancellation"))
internal val ARRAY_OF = CallableId(FqName("kotlin"), Name.identifier("arrayOf"))

internal const val ABI_VERSION = "VERSION"
internal const val UNORDERED = "UNORDERED"
internal const val LOOKUP_BY_NAME = "BY_NAME"
internal const val LOOKUP_BY_TYPE = "BY_TYPE"
internal const val WITH_CONTEXT = "withContext"
internal val SLOT_READS = setOf("captured", "isCaptured", "isNull")

internal object ParameterNames {
    const val KIND = "kind"
    const val NAME = "name"
    const val BLOCK = "block"
    const val RELAXED = "relaxed"
    const val RELAX_UNIT_FUN = "relaxUnitFun"
    const val MORE_INTERFACES = "moreInterfaces"
    const val SPIED_INSTANCE = "obj"
    const val INTERFACE_TYPE = "type"
    const val LOOKUP_TYPE = "lookupType"
    const val CLEAR = "clear"
    const val ORDERING = "ordering"
    const val EXACTLY = "exactly"
    const val AT_LEAST = "atLeast"
    const val AT_MOST = "atMost"
    val REFERENCES = setOf("function", "functions", "property", "properties")
}

internal object MatcherNames {
    const val ALL_ANY = "allAny"
    const val ARRAY = "array"
    const val TYPED_FACTORY = "typed"
    const val SPREAD_FACTORY = "spread"
    const val VARARG_OF_FACTORY = "varargOf"
    val ANY = setOf("any", "anyNullable", ALL_ANY)
    val COMPOSITE = setOf("and", "or", "not")
    val CAPTURE = setOf("capture", "captureNullable")
    val WITH_ARG = setOf("withArg", "withNullableArg", "coWithArg", "coWithNullableArg")
    val REF_EQ = setOf("refEq", "nrefEq")
    val VARARG =
        setOf("anyVararg", "varargAll", "varargAllNullable", "varargAny", "varargAnyNullable") +
            listOf("Boolean", "Byte", "Char", "Short", "Int", "Long", "Float", "Double")
                .flatMap { listOf("any${it}Vararg", "varargAll$it", "varargAny$it") }
    val TYPED =
        setOf("any", "anyNullable", "match", "matchNullable", "coMatch", "coMatchNullable", ARRAY) +
            setOf("captureLambda", "captureCoroutine", "cmpEq", "more", "less", "range") + WITH_ARG + CAPTURE
    val BUILTIN = TYPED + COMPOSITE + VARARG + REF_EQ + setOf(ALL_ANY, "eq", "neq", "isNull", "ofType")
}

internal object AnswerNames {
    const val RETURNS_ARGUMENT = "returnsArgument"
    val ORIGINAL_CALLS = setOf("callOriginal", "coCallOriginal")
    val BLOCKS = setOf("answers", "coAnswers", "andThenAnswer", "coAndThen")
}
