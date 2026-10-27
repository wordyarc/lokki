package io.lokki

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS, AnnotationTarget.PROPERTY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal lokki API that should not be used from outside of lokki. " +
        "No compatibility guarantees are provided.",
)
public annotation class InternalLokkiApi

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS,
)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This lokki API is experimental: its design and behaviour may change in future releases.",
)
public annotation class ExperimentalLokkiApi

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS, AnnotationTarget.PROPERTY)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is a delicate lokki API and its use requires care: " +
        "it has side effects or pitfalls that ordinary use of lokki does not have.",
)
public annotation class DelicateLokkiApi

@DslMarker
internal annotation class LokkiDslMarker
