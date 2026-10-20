package io.lokki

import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.states

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> every(stubBlock: MatcherScope.() -> T): StubScope<T> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> coEvery(stubBlock: suspend MatcherScope.() -> T): StubScope<T> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun justRun(stubBlock: MatcherScope.() -> Unit): AdditionalAnswerScope<Unit> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun coJustRun(stubBlock: suspend MatcherScope.() -> Unit): AdditionalAnswerScope<Unit> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun coJustAwait(stubBlock: suspend MatcherScope.() -> Unit): AdditionalAnswerScope<Unit> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public fun checkUnnecessaryStub(vararg mocks: Any) {
    states("checkUnnecessaryStub", mocks).forEach { it.checkUnnecessaryStub() }
}

@SubclassOptInRequired(InternalLokkiApi::class)
public interface StubScope<T> {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun answers(answer: Answer<T>): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun answers(answer: AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun coAnswers(answer: suspend AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun returns(returnValue: T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun returnsMany(values: List<T>): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public fun returnsMany(vararg values: T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun returnsArgument(n: Int): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun throws(ex: Throwable): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun throwsMany(exList: List<Throwable>): AdditionalAnswerScope<T>
}

@SubclassOptInRequired(InternalLokkiApi::class)
public interface AdditionalAnswerScope<T> {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenAnswer(answer: Answer<T>): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenAnswer(answer: AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun coAndThen(answer: suspend AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThen(returnValue: T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenMany(values: List<T>): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public fun andThenMany(vararg values: T): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenThrows(ex: Throwable): AdditionalAnswerScope<T>

    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public infix fun andThenThrowsMany(exList: List<Throwable>): AdditionalAnswerScope<T>
}

public object Runs

@LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
public infix fun StubScope<Unit>.just(runs: Runs): AdditionalAnswerScope<Unit> = returns(Unit)

@LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
public infix fun AdditionalAnswerScope<Unit>.andThenJust(runs: Runs): AdditionalAnswerScope<Unit> = andThen(Unit)

public object Awaits

@LokkiIntrinsic(LokkiIntrinsic.Kind.AWAITS)
public infix fun <T> StubScope<T>.just(awaits: Awaits): AdditionalAnswerScope<T> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.AWAITS)
public infix fun <T> AdditionalAnswerScope<T>.andThenJust(awaits: Awaits): AdditionalAnswerScope<T> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")
