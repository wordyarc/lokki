package io.lokki

import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.MockFieldsOwner
import io.lokki.internal.RECORD_PRIVATE_CALLS
import io.lokki.internal.RELAXED
import io.lokki.internal.RELAX_UNIT_FUN
import io.lokki.internal.pluginNotApplied
import io.lokki.internal.uninitializedMockPropertiesOwner
import kotlin.reflect.KClass

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
public annotation class Mock(
    public val name: String = "",
    public val relaxed: Boolean = false,
    public val relaxUnitFun: Boolean = false,
)

@LokkiIntrinsic(LokkiIntrinsic.Kind.RELAXED_MOCK)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
public annotation class RelaxedMock(public val name: String = "")

@LokkiIntrinsic(LokkiIntrinsic.Kind.SPY)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
public annotation class Spy(public val name: String = "", public val recordPrivateCalls: Boolean = true)

@LokkiIntrinsic(LokkiIntrinsic.Kind.ADDITIONAL_INTERFACE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
public annotation class AdditionalInterface(public val type: KClass<*>)

@LokkiIntrinsic(LokkiIntrinsic.Kind.INJECT_MOCKS)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
public annotation class InjectMocks(
    public val lookupType: InjectionLookupType = InjectionLookupType.BOTH,
    public val overrideValues: Boolean = false,
)

@LokkiIntrinsic(LokkiIntrinsic.Kind.INJECT_MOCKS)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
public annotation class OverrideMocks(public val lookupType: InjectionLookupType = InjectionLookupType.BOTH)

public enum class InjectionLookupType {
    BY_NAME,
    BY_TYPE,
    BOTH,
    ;

    public val byName: Boolean
        get() = this == BY_NAME || this == BOTH

    public val byType: Boolean
        get() = this == BY_TYPE || this == BOTH
}

public object MockAnnotations {
    public fun init(
        vararg obj: Any,
        overrideRecordPrivateCalls: Boolean = false,
        relaxUnitFun: Boolean = false,
        relaxed: Boolean = false,
    ) {
        val flags = (if (relaxed) RELAXED else 0) or (if (relaxUnitFun) RELAX_UNIT_FUN else 0) or
            (if (overrideRecordPrivateCalls) RECORD_PRIVATE_CALLS else 0)
        for (target in obj) {
            if (target is MockFieldsOwner) {
                target.`lokki$initMockFields`(flags)
            } else {
                uninitializedMockPropertiesOwner(target)?.let { owner ->
                    pluginNotApplied("the compilation of $owner, so its mock properties are not initialized")
                }
            }
        }
    }
}
