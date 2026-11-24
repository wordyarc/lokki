package io.lokki.compiler

internal enum class IntrinsicKind {
    MOCK,
    SPY,
    RELAXED_MOCK,
    ADDITIONAL_INTERFACE,
    INJECT_MOCKS,
    STUB_BLOCK,
    JUST_RUN_BLOCK,
    JUST_AWAIT_BLOCK,
    VERIFY_BLOCK,
    VERIFY_ALL_BLOCK,
    VERIFY_ORDER_BLOCK,
    VERIFY_SEQUENCE_BLOCK,
    VERIFY_ANY_BLOCK,
    EXCLUDE_BLOCK,
    WAS_NOT_CALLED,
    WITH_CONTEXT,
    MATCHER,
    ANSWER,
    AWAITS,
    MOCK_OBJECT,
    MOCK_STATIC,
    UNMOCK_STATIC,
    CLEAR_STATIC_MOCK,
    CONFIRM_VERIFIED_STATIC,
    MOCK_CONSTRUCTOR,
    UNMOCK_CONSTRUCTOR,
    CLEAR_CONSTRUCTOR_MOCK,
    CONSTRUCTED,
    DYNAMIC_MEMBER,
    DYNAMIC_CALL,
    EXTERNAL_MOCK,
    EXTERNAL_SPY,
    UNKNOWN;

    companion object {
        fun of(name: String): IntrinsicKind = entries.find { it.name == name } ?: UNKNOWN
    }
}

internal val STUB_BLOCKS =
    setOf(IntrinsicKind.STUB_BLOCK, IntrinsicKind.JUST_RUN_BLOCK, IntrinsicKind.JUST_AWAIT_BLOCK)

internal val VERIFICATION_ORDERINGS = mapOf(
    IntrinsicKind.VERIFY_ALL_BLOCK to "ALL",
    IntrinsicKind.VERIFY_ORDER_BLOCK to "ORDERED",
    IntrinsicKind.VERIFY_SEQUENCE_BLOCK to "SEQUENCE",
    IntrinsicKind.VERIFY_ANY_BLOCK to "ANY",
)

internal val ORDERED_VERIFY_BLOCKS = VERIFICATION_ORDERINGS.keys - IntrinsicKind.VERIFY_ANY_BLOCK

internal val VERIFY_BLOCKS = VERIFICATION_ORDERINGS.keys + IntrinsicKind.VERIFY_BLOCK

internal val BLOCK_KINDS = STUB_BLOCKS + VERIFY_BLOCKS + IntrinsicKind.EXCLUDE_BLOCK

internal val STATIC_KINDS = setOf(
    IntrinsicKind.MOCK_STATIC,
    IntrinsicKind.UNMOCK_STATIC,
    IntrinsicKind.CLEAR_STATIC_MOCK,
    IntrinsicKind.CONFIRM_VERIFIED_STATIC,
)

internal val CONSTRUCTOR_KINDS = setOf(
    IntrinsicKind.MOCK_CONSTRUCTOR,
    IntrinsicKind.UNMOCK_CONSTRUCTOR,
    IntrinsicKind.CLEAR_CONSTRUCTOR_MOCK,
)

internal val EXTERNAL_KINDS = setOf(IntrinsicKind.EXTERNAL_MOCK, IntrinsicKind.EXTERNAL_SPY)

internal val FIELD_KINDS =
    setOf(IntrinsicKind.MOCK, IntrinsicKind.RELAXED_MOCK, IntrinsicKind.SPY, IntrinsicKind.INJECT_MOCKS)

internal val ENTRY_POINTS =
    BLOCK_KINDS + STATIC_KINDS + CONSTRUCTOR_KINDS +
        setOf(IntrinsicKind.MOCK, IntrinsicKind.SPY, IntrinsicKind.MOCK_OBJECT)

internal val IntrinsicKind?.isReferable: Boolean
    get() = this == null || this == IntrinsicKind.ANSWER
