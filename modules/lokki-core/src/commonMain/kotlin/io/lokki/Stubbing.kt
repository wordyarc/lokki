package io.lokki

import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.pluginNotApplied
import io.lokki.internal.window
import kotlin.reflect.KClass

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> every(stubBlock: MatcherScope.() -> T): StubScope<T, T> =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> coEvery(stubBlock: suspend MatcherScope.() -> T): StubScope<T, T> =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.JUST_RUN_BLOCK)
public fun justRun(stubBlock: MatcherScope.() -> Unit): AdditionalAnswerScope<Unit, Unit> =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.JUST_RUN_BLOCK)
public fun coJustRun(stubBlock: suspend MatcherScope.() -> Unit): AdditionalAnswerScope<Unit, Unit> =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.JUST_AWAIT_BLOCK)
public fun coJustAwait(stubBlock: suspend MatcherScope.() -> Unit): AdditionalAnswerScope<Unit, Unit> =
    pluginNotApplied()

public inline fun <T> lenient(block: LenientStubbingScope.() -> T): T = LenientStubbingScope().block()

@LokkiDslMarker
public class LenientStubbingScope @PublishedApi internal constructor() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
    public fun <T> every(stubBlock: MatcherScope.() -> T): StubScope<T, T> =
        pluginNotApplied()

    @LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
    public fun <T> coEvery(stubBlock: suspend MatcherScope.() -> T): StubScope<T, T> =
        pluginNotApplied()

    @LokkiIntrinsic(LokkiIntrinsic.Kind.JUST_RUN_BLOCK)
    public fun justRun(stubBlock: MatcherScope.() -> Unit): AdditionalAnswerScope<Unit, Unit> =
        pluginNotApplied()

    @LokkiIntrinsic(LokkiIntrinsic.Kind.JUST_RUN_BLOCK)
    public fun coJustRun(stubBlock: suspend MatcherScope.() -> Unit): AdditionalAnswerScope<Unit, Unit> =
        pluginNotApplied()
}

public fun checkUnnecessaryStub(vararg mocks: Any) {
    val window = window("checkUnnecessaryStub", mocks)
    window.states.forEach { it.checkUnnecessaryStub(window.since) }
}

@SubclassOptInRequired(InternalLokkiApi::class)
public interface StubScope<T, B> {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun answers(answer: Answer<T>): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun answers(answer: AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun coAnswers(answer: suspend AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun returns(returnValue: T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun returnsMany(values: List<T>): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public fun returnsMany(vararg values: T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun returnsArgument(n: Int): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun throws(ex: Throwable): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun throwsMany(exList: List<Throwable>): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun <K : Any> propertyType(cls: KClass<K>): StubScope<T, K>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun <K : Any> nullablePropertyType(cls: KClass<K>): StubScope<T, K?>
}

@SubclassOptInRequired(InternalLokkiApi::class)
public interface AdditionalAnswerScope<T, B> {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenAnswer(answer: Answer<T>): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenAnswer(answer: AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun coAndThen(answer: suspend AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThen(returnValue: T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenMany(values: List<T>): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public fun andThenMany(vararg values: T): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenThrows(ex: Throwable): AdditionalAnswerScope<T, B>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenThrowsMany(exList: List<Throwable>): AdditionalAnswerScope<T, B>
}

public object Runs

public typealias runs = Runs

@LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
public infix fun StubScope<Unit, Unit>.just(runs: Runs): AdditionalAnswerScope<Unit, Unit> = returns(Unit)

@LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
public infix fun AdditionalAnswerScope<Unit, Unit>.andThenJust(runs: Runs): AdditionalAnswerScope<Unit, Unit> = andThen(Unit)

public object Awaits

public typealias awaits = Awaits

@LokkiIntrinsic(LokkiIntrinsic.Kind.AWAITS)
public infix fun <T, B> StubScope<T, B>.just(awaits: Awaits): AdditionalAnswerScope<T, B> =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.AWAITS)
public infix fun <T, B> AdditionalAnswerScope<T, B>.andThenJust(awaits: Awaits): AdditionalAnswerScope<T, B> =
    pluginNotApplied()
