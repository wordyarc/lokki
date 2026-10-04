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
    }
}
