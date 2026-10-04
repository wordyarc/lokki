package io.lokki

import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> every(stubBlock: MatcherScope.() -> T): StubScope<T> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@SubclassOptInRequired(InternalLokkiApi::class)
public interface StubScope<T> {
    public infix fun answers(answer: Answer<T>): AdditionalAnswerScope<T>

    public infix fun answers(answer: AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T>

    public infix fun returns(returnValue: T): AdditionalAnswerScope<T>

    public infix fun returnsMany(values: List<T>): AdditionalAnswerScope<T>

    public fun returnsMany(vararg values: T): AdditionalAnswerScope<T>

    public infix fun returnsArgument(n: Int): AdditionalAnswerScope<T>

    public infix fun throws(ex: Throwable): AdditionalAnswerScope<T>

    public infix fun throwsMany(exList: List<Throwable>): AdditionalAnswerScope<T>
}

@SubclassOptInRequired(InternalLokkiApi::class)
public interface AdditionalAnswerScope<T> {
    public infix fun andThenAnswer(answer: Answer<T>): AdditionalAnswerScope<T>

    public infix fun andThenAnswer(answer: AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T>

    public infix fun andThen(returnValue: T): AdditionalAnswerScope<T>

    public infix fun andThenMany(values: List<T>): AdditionalAnswerScope<T>

    public fun andThenMany(vararg values: T): AdditionalAnswerScope<T>

    public infix fun andThenThrows(ex: Throwable): AdditionalAnswerScope<T>

    public infix fun andThenThrowsMany(exList: List<Throwable>): AdditionalAnswerScope<T>
}

public object Runs

public infix fun StubScope<Unit>.just(runs: Runs): AdditionalAnswerScope<Unit> = returns(Unit)

public infix fun AdditionalAnswerScope<Unit>.andThenJust(runs: Runs): AdditionalAnswerScope<Unit> = andThen(Unit)
