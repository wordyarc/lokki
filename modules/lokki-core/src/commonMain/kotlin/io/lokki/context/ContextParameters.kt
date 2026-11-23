package io.lokki.context

import io.lokki.MatcherScope
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.pluginNotApplied

@LokkiIntrinsic(LokkiIntrinsic.Kind.WITH_CONTEXT)
public inline fun <reified C1 : Any> MatcherScope.withContext(noinline stubBlock: context(C1) () -> Any?): Any? =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.WITH_CONTEXT)
public inline fun <reified C1 : Any, reified C2 : Any> MatcherScope.withContext(
    noinline stubBlock: context(C1, C2) () -> Any?,
): Any? =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.WITH_CONTEXT)
public inline fun <reified C1 : Any, reified C2 : Any, reified C3 : Any> MatcherScope.withContext(
    noinline stubBlock: context(C1, C2, C3) () -> Any?,
): Any? =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.WITH_CONTEXT)
public inline fun <reified C1 : Any, reified C2 : Any, reified C3 : Any, reified C4 : Any> MatcherScope.withContext(
    noinline stubBlock: context(C1, C2, C3, C4) () -> Any?,
): Any? =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.WITH_CONTEXT)
public inline fun <
    reified C1 : Any,
    reified C2 : Any,
    reified C3 : Any,
    reified C4 : Any,
    reified C5 : Any,
> MatcherScope.withContext(
    noinline stubBlock: context(C1, C2, C3, C4, C5) () -> Any?,
): Any? =
    pluginNotApplied()
