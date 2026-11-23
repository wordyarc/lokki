package io.lokki

import io.lokki.internal.GeneratedMock
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.Redirects
import io.lokki.internal.pluginNotApplied
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
): T = pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK)
public inline fun <T : Any> mockClass(
    type: KClass<T>,
    name: String? = null,
    relaxed: Boolean = false,
    vararg moreInterfaces: KClass<*>,
    relaxUnitFun: Boolean = false,
    block: T.() -> Unit = {},
): T = pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.SPY)
public inline fun <reified T : Any> spy(
    name: String? = null,
    vararg moreInterfaces: KClass<*>,
    block: T.() -> Unit = {},
): T = pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.SPY)
public inline fun <reified T : Any> spy(
    obj: T,
    name: String? = null,
    vararg moreInterfaces: KClass<*>,
    block: T.() -> Unit = {},
): T = pluginNotApplied()

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
public annotation class ChainedMocks

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_OBJECT)
public fun mockObject(vararg objects: Any): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_OBJECT)
public inline fun mockObject(vararg objects: Any, block: () -> Unit): Unit =
    pluginNotApplied()

public fun unmockObject(vararg objects: Any) {
    Redirects.unmock(objects)
}

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public fun mockStatic(vararg functions: KFunction<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public fun mockStatic(vararg functions: KProperty<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public inline fun mockStatic(vararg functions: KFunction<*>, block: () -> Unit): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public inline fun mockStatic(vararg functions: KProperty<*>, block: () -> Unit): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public fun mockStatic(vararg classes: String): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_STATIC)
public inline fun mockStatic(vararg classes: String, block: () -> Unit): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.UNMOCK_STATIC)
public fun unmockStatic(vararg classes: String): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.UNMOCK_STATIC)
public fun unmockStatic(vararg functions: KFunction<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.UNMOCK_STATIC)
public fun unmockStatic(vararg functions: KProperty<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.CLEAR_STATIC_MOCK)
public fun clearStaticMock(vararg functions: KFunction<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.CLEAR_STATIC_MOCK)
public fun clearStaticMock(vararg functions: KProperty<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_CONSTRUCTOR)
public fun mockConstructor(vararg classes: KClass<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_CONSTRUCTOR)
public inline fun mockConstructor(vararg classes: KClass<*>, block: () -> Unit): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.UNMOCK_CONSTRUCTOR)
public fun unmockConstructor(vararg classes: KClass<*>): Unit =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.CLEAR_CONSTRUCTOR_MOCK)
public fun clearConstructorMock(
    vararg classes: KClass<*>,
    answers: Boolean = true,
    recordedCalls: Boolean = true,
    childMocks: Boolean = true,
): Unit = pluginNotApplied()

public fun unmockAll() {
    Redirects.unmockAll()
}

public fun isMock(
    mock: Any,
    regular: Boolean = true,
    spy: Boolean = false,
    objectMock: Boolean = false,
    staticMock: Boolean = false,
    constructorMock: Boolean = false,
): Boolean {
    val state = (mock as? GeneratedMock)?.`lokki$state`
    return regular && state?.isRegular == true ||
        spy && state?.isSpy == true ||
        objectMock && Redirects.isObject(mock) ||
        staticMock && Redirects.isStatic(mock) ||
        constructorMock && Redirects.isConstructor(mock)
}

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
) {
    val scope = LokkiScope.requireCurrent("clearAllMocks")
    if (!regularMocks && !objectMocks && !staticMocks && !constructorMocks) return
    val states = scope.windowStates().filter {
        when {
            it.isObject -> objectMocks
            it.isStatic -> staticMocks
            it.isConstructor -> constructorMocks
            else -> regularMocks
        }
    }
    scope.clearWindow(states, answers, recordedCalls, childMocks)
}
