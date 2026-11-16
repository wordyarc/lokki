package io.lokki.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal object CoreClasses {
    val GENERATED_MOCK = internalClassId("GeneratedMock")
    val GENERATED_MOCK_COMPANION = GENERATED_MOCK.createNestedClassId(Name.identifier("Companion"))
    val METHOD_DESCRIPTIONS = internalClassId("MethodDescriptions")
    val REDIRECTS = internalClassId("Redirects")
    val MOCK_FIELDS_OWNER = internalClassId("MockFieldsOwner")
    val STATIC_INITIALIZER = internalClassId("StaticInitializer")
    val INSTANCE_ALLOCATOR = internalClassId("InstanceAllocator")
    val MOCK_STATE = internalClassId("MockState")
    val VERIFICATION = internalClassId("Verification")
    val STUB_SCOPE = publicClassId("StubScope")
    val ADDITIONAL_ANSWER_SCOPE = publicClassId("AdditionalAnswerScope")
    val MATCHER_SCOPE = publicClassId("MatcherScope")
}

internal enum class CoreFunction(val owner: ClassId, val functionName: String, val valueParameters: Int? = null) {
    ORIGINAL(CoreClasses.GENERATED_MOCK, "lokki\$original"),
    CO_ORIGINAL(CoreClasses.GENERATED_MOCK, "lokki\$coOriginal"),
    RELAXED(CoreClasses.GENERATED_MOCK, "lokki\$relaxed"),
    DESCRIBE(CoreClasses.GENERATED_MOCK, "lokki\$describe"),
    OF(CoreClasses.GENERATED_MOCK_COMPANION, "of"),
    WITHOUT_STATE_CHANGES(CoreClasses.GENERATED_MOCK_COMPANION, "withoutStateChanges"),
    EVALUATE(CoreClasses.GENERATED_MOCK_COMPANION, "evaluate"),
    CHAIN(CoreClasses.GENERATED_MOCK_COMPANION, "chain"),
    LINK(CoreClasses.GENERATED_MOCK_COMPANION, "link"),
    METHOD_DESCRIPTION(CoreClasses.METHOD_DESCRIPTIONS, "of"),
    MOCK_OBJECT(CoreClasses.REDIRECTS, "mockObject", valueParameters = 1),
    MOCK_OBJECT_WITH_BLOCK(CoreClasses.REDIRECTS, "mockObject", valueParameters = 2),
    MOCK_STATIC(CoreClasses.REDIRECTS, "mockStatic", valueParameters = 1),
    MOCK_STATIC_WITH_BLOCK(CoreClasses.REDIRECTS, "mockStatic", valueParameters = 2),
    REDIRECT_OF(CoreClasses.REDIRECTS, "of"),
    STATIC_REDIRECT_OF(CoreClasses.REDIRECTS, "ofStatic"),
    UNMOCK(CoreClasses.REDIRECTS, "unmock"),
    INIT_MOCK_FIELDS(CoreClasses.MOCK_FIELDS_OWNER, INIT_MOCK_FIELDS_NAME),
    ENTER_STATIC_INITIALIZER(CoreClasses.STATIC_INITIALIZER, "enter"),
    EXIT_STATIC_INITIALIZER(CoreClasses.STATIC_INITIALIZER, "exit"),
    ALLOCATE(CoreClasses.INSTANCE_ALLOCATOR, "allocate"),
    ANSWER(CoreClasses.MOCK_STATE, "answer"),
    CO_ANSWER(CoreClasses.MOCK_STATE, "coAnswer"),
    NO_ORIGINAL(CoreClasses.MOCK_STATE, "noOriginal"),
    STUB(CoreClasses.MOCK_STATE, "stub"),
    CAPTURE_DEFAULTS(CoreClasses.MOCK_STATE, "captureDefaults"),
    CO_CAPTURE_DEFAULTS(CoreClasses.MOCK_STATE, "coCaptureDefaults"),
    EXCLUDE(CoreClasses.MOCK_STATE, "exclude"),
    MOCK_STATE_LINK(CoreClasses.MOCK_STATE, "link"),
    EXPECT(CoreClasses.VERIFICATION, "expect"),
    EXPECT_LINK(CoreClasses.VERIFICATION, "expectLink"),
    CHECK(CoreClasses.VERIFICATION, "check"),
    RETURNS(CoreClasses.STUB_SCOPE, "returns"),
    CO_ANSWERS(CoreClasses.STUB_SCOPE, "coAnswers"),
    CO_AND_THEN(CoreClasses.ADDITIONAL_ANSWER_SCOPE, "coAndThen"),
    MATCHER_OF(CoreClasses.MATCHER_SCOPE, "matcherOf"),
    ANY(CoreClasses.MATCHER_SCOPE, "any"),
}

internal const val STATE_PROPERTY_NAME = "lokki\$state"
internal const val INIT_MOCK_FIELDS_NAME = "lokki\$initMockFields"

internal class IncompatibleRuntimeException(val problem: String) : RuntimeException(problem)
