package io.lokki

import io.lokki.internal.LokkiIntrinsic

public abstract class MatcherScope internal constructor() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T> any(): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T> anyNullable(): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> eq(value: T, inverse: Boolean = false): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> neq(value: T): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> refEq(value: T, inverse: Boolean = false): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> nrefEq(value: T): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> cmpEq(value: T): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> more(value: T, andEquals: Boolean = false): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> less(value: T, andEquals: Boolean = false): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> range(
        from: T,
        to: T,
        fromInclusive: Boolean = true,
        toInclusive: Boolean = true,
    ): T = throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> and(left: T, right: T): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> or(left: T, right: T): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> not(value: T): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> isNull(inverse: Boolean = false): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> ofType(): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> match(noinline matcher: (T) -> Boolean): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> matchNullable(noinline matcher: (T?) -> Boolean): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")
}
