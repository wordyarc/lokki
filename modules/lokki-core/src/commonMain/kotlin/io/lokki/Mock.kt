package io.lokki

import io.lokki.internal.GeneratedMock
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.states
import kotlin.reflect.KClass

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK)
public inline fun <reified T : Any> mock(
    name: String? = null,
    relaxed: Boolean = false,
    vararg moreInterfaces: KClass<*>,
    relaxUnitFun: Boolean = false,
    block: T.() -> Unit = {},
): T = throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.SPY)
public inline fun <reified T : Any> spy(
    name: String? = null,
    vararg moreInterfaces: KClass<*>,
    block: T.() -> Unit = {},
): T = throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.SPY)
public inline fun <reified T : Any> spy(
    obj: T,
    name: String? = null,
    vararg moreInterfaces: KClass<*>,
    block: T.() -> Unit = {},
): T = throw LokkiException("lokki compiler plugin is not applied to this compilation")

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
public annotation class ChainedMocks

public fun isMock(obj: Any): Boolean = obj is GeneratedMock

public fun clearMocks(
    firstMock: Any,
    vararg mocks: Any,
    answers: Boolean = true,
    recordedCalls: Boolean = true,
    childMocks: Boolean = true,
    verificationMarks: Boolean = true,
    exclusionRules: Boolean = true,
) {
    for (state in states("clearMocks", arrayOf(firstMock, *mocks))) {
        state.clear(answers, recordedCalls, childMocks, verificationMarks, exclusionRules)
    }
}
