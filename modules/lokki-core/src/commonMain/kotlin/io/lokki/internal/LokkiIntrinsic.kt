package io.lokki.internal

import io.lokki.InternalLokkiApi

@InternalLokkiApi
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.ANNOTATION_CLASS)
public annotation class LokkiIntrinsic(val kind: Kind) {
    public enum class Kind {
        MOCK,
        STUB_BLOCK,
        MATCHER,
        ANSWER,
        VERIFY_BLOCK,
        WAS_NOT_CALLED,
        EXCLUDE_BLOCK,
        AWAITS,
        SPY,
        RELAXED_MOCK,
        ADDITIONAL_INTERFACE,
        INJECT_MOCKS,
    }
}
