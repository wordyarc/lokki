package io.lokki.spring

import io.lokki.internal.LokkiIntrinsic
import org.springframework.core.annotation.AliasFor
import org.springframework.test.context.bean.override.BeanOverride
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@BeanOverride(MockBeanOverrideProcessor::class)
@LokkiIntrinsic(LokkiIntrinsic.Kind.EXTERNAL_MOCK)
public annotation class MockBean(
    @get:AliasFor("name")
    val value: String = "",
    @get:AliasFor("value")
    val name: String = "",
    val contextName: String = "",
    val extraInterfaces: Array<KClass<*>> = [],
    val clear: MockClear = MockClear.AFTER,
    val enforceOverride: Boolean = false,
    val relaxed: Boolean = false,
    val relaxUnitFun: Boolean = false,
)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@BeanOverride(MockBeanOverrideProcessor::class)
@LokkiIntrinsic(LokkiIntrinsic.Kind.EXTERNAL_SPY)
public annotation class SpyBean(
    @get:AliasFor("name")
    val value: String = "",
    @get:AliasFor("value")
    val name: String = "",
    val contextName: String = "",
    val clear: MockClear = MockClear.AFTER,
)

public enum class MockClear {
    BEFORE,
    AFTER,
    NONE,
}
