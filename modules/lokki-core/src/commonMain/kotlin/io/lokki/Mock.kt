package io.lokki

import io.lokki.internal.GeneratedMock
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.Redirects
import io.lokki.internal.states
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

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

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_OBJECT)
public fun mockObject(vararg objects: Any): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_OBJECT)
public inline fun mockObject(vararg objects: Any, block: () -> Unit): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public fun unmockObject(vararg objects: Any) {
    Redirects.unmock(objects)
}

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public fun mockStatic(vararg functions: KFunction<*>): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public fun mockStatic(vararg functions: KProperty<*>): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public inline fun mockStatic(vararg functions: KFunction<*>, block: () -> Unit): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public inline fun mockStatic(vararg functions: KProperty<*>, block: () -> Unit): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STATIC_MOCKS)
public fun unmockStatic(vararg functions: KFunction<*>): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STATIC_MOCKS)
public fun unmockStatic(vararg functions: KProperty<*>): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STATIC_MOCKS)
public fun clearStaticMock(vararg functions: KFunction<*>): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STATIC_MOCKS)
public fun clearStaticMock(vararg functions: KProperty<*>): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public fun unmockAll() {
    Redirects.unmockAll()
}

public fun isMock(obj: Any): Boolean = obj is GeneratedMock || Redirects.find(obj) != null

public fun clearMocks(
    firstMock: Any,
    vararg mocks: Any,
    answers: Boolean = true,
    recordedCalls: Boolean = true,
    childMocks: Boolean = true,
    verificationMarks: Boolean = true,
    exclusionRules: Boolean = true,
) {
    for (state in states(arrayOf(firstMock, *mocks))) {
        state.clear(answers, recordedCalls, childMocks, verificationMarks, exclusionRules)
    }
}

public fun clearAllMocks(
    answers: Boolean = true,
    recordedCalls: Boolean = true,
    childMocks: Boolean = true,
    regularMocks: Boolean = true,
    objectMocks: Boolean = true,
    staticMocks: Boolean = true,
    constructorMocks: Boolean = true,
    currentThreadOnly: Boolean = false,
) {
    val scope = LokkiScope.requireCurrent("clearAllMocks")
    if (!regularMocks && !objectMocks && !staticMocks) return
    val states = scope.windowStates().filter {
        when {
            it.isObject -> objectMocks
            it.isStatic -> staticMocks
            else -> regularMocks
        }
    }
    scope.clearWindow(states, answers, recordedCalls, childMocks)
}
