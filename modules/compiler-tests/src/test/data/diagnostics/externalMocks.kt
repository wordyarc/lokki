// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
@file:OptIn(InternalLokkiApi::class)

package sample

import io.lokki.InternalLokkiApi
import io.lokki.internal.LokkiIntrinsic

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@LokkiIntrinsic(LokkiIntrinsic.Kind.EXTERNAL_MOCK)
annotation class Bean

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@LokkiIntrinsic(LokkiIntrinsic.Kind.EXTERNAL_SPY)
annotation class SpyBean

interface Repo {
    fun find(id: Int): String
}

class Final

class Holder {
    <!LOKKI_MOCK_PROPERTY_NOT_MUTABLE!>@Bean<!>
    val repo: Repo = TODO()

    <!LOKKI_TYPE_NOT_MOCKABLE!>@Bean<!>
    lateinit var final: Final

    @SpyBean
    lateinit var spied: Repo
}
