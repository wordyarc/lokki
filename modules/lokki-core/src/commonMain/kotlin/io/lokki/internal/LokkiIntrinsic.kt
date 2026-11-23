package io.lokki.internal

import io.lokki.InternalLokkiApi

@InternalLokkiApi
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.ANNOTATION_CLASS)
public annotation class LokkiIntrinsic(val kind: Kind) {
    public enum class Kind {
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
    }
}
