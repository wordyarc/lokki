package io.lokki.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val LOKKI_PACKAGE = FqName("io.lokki")
internal val LOKKI_INTERNAL_PACKAGE = FqName("io.lokki.internal")

internal fun publicClassId(name: String): ClassId = ClassId(LOKKI_PACKAGE, Name.identifier(name))

internal fun internalClassId(name: String): ClassId = ClassId(LOKKI_INTERNAL_PACKAGE, Name.identifier(name))

internal val AWAIT_CANCELLATION = CallableId(FqName("kotlinx.coroutines"), Name.identifier("awaitCancellation"))
internal val EMPTY_FLOW = CallableId(FqName("kotlinx.coroutines.flow"), Name.identifier("emptyFlow"))
internal val OVERRIDE_MOCKS = publicClassId("OverrideMocks")
internal val ARRAY_OF = CallableId(FqName("kotlin"), Name.identifier("arrayOf"))

internal const val ABI_VERSION = "VERSION"
internal const val UNORDERED = "UNORDERED"
internal const val ANY_ORDERING = "ANY"
internal const val LOOKUP_BY_NAME = "BY_NAME"
internal const val LOOKUP_BY_TYPE = "BY_TYPE"
internal const val WITH_CONTEXT = "withContext"
internal const val FINALIZE = "finalize"
internal const val FINALIZER_REASON = "it is the JVM finalizer of its class, which runs the original code"
internal val SLOT_READS = setOf("captured", "isCaptured", "isNull")

internal object ParameterNames {
    const val KIND = "kind"
    const val NAME = "name"
    const val BLOCK = "block"
    const val RELAXED = "relaxed"
    const val RELAX_UNIT_FUN = "relaxUnitFun"
    const val MORE_INTERFACES = "moreInterfaces"
    const val EXTRA_INTERFACES = "extraInterfaces"
    const val SPIED_INSTANCE = "obj"
    const val INTERFACE_TYPE = "type"
    const val LOOKUP_TYPE = "lookupType"
    const val OVERRIDE_VALUES = "overrideValues"
    const val MOCKED_CLASS = "type"
    const val CLEAR = "clear"
    const val ORDERING = "ordering"
    const val EXACTLY = "exactly"
    const val AT_LEAST = "atLeast"
    const val AT_MOST = "atMost"
    const val FILE_CLASSES = "classes"
    const val ANSWERS = "answers"
    const val RECORDED_CALLS = "recordedCalls"
    const val CHILD_MOCKS = "childMocks"
    const val RECORD_PRIVATE_CALLS = "recordPrivateCalls"
    val REFERENCES = setOf("function", "functions", "property", "properties")
}

internal object MatcherNames {
    const val ALL_ANY = "allAny"
    const val ARRAY = "array"
    const val TYPED_FACTORY = "typed"
    const val SPREAD_FACTORY = "spread"
    const val VARARG_OF_FACTORY = "varargOf"
    val ANY = setOf("any", "anyNullable", ALL_ANY)
    val CLASSIFIED = setOf("any", "match")
    val COMPOSITE = setOf("and", "or", "not")
    val CAPTURE = setOf("capture", "captureNullable")
    val WITH_ARG = setOf("withArg", "withNullableArg", "coWithArg", "coWithNullableArg")
    val REF_EQ = setOf("refEq", "nrefEq")
    val VARARG =
        setOf("anyVararg", "varargAll", "varargAllNullable", "varargAny", "varargAnyNullable", "varargArray") +
            listOf("Boolean", "Byte", "Char", "Short", "Int", "Long", "Float", "Double")
                .flatMap { listOf("any${it}Vararg", "varargAll$it", "varargAny$it") }
    val TYPED =
        setOf("any", "anyNullable", "match", "matchNullable", "coMatch", "coMatchNullable", ARRAY) +
            setOf("captureLambda", "captureCoroutine", "cmpEq", "more", "less", "range") + WITH_ARG + CAPTURE
    val BUILTIN = TYPED + COMPOSITE + VARARG + REF_EQ + setOf(ALL_ANY, "eq", "neq", "isNull", "ofType")
}

internal object DynamicNames {
    const val INVOKE = "invoke"
    const val WITH_ARGUMENTS = "withArguments"
    const val INVOKE_NO_ARGS = "invokeNoArgs"
    const val GET_PROPERTY = "getProperty"
    const val VALUE = "value"
    val LIST_FACTORIES = setOf(FqName("kotlin.collections.listOf"), FqName("kotlin.collections.emptyList"))
}

internal object AnswerNames {
    const val RETURNS_ARGUMENT = "returnsArgument"
    val ORIGINAL_CALLS = setOf("callOriginal", "coCallOriginal")
    val BLOCKS = setOf("answers", "coAnswers", "andThenAnswer", "coAndThen")
    val FIELD_VALUES = setOf("fieldValue", "fieldValueAny")
}
