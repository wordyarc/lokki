package io.lokki

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS, AnnotationTarget.PROPERTY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal lokki API that should not be used from outside of lokki. " +
        "No compatibility guarantees are provided.",
)
public annotation class InternalLokkiApi
