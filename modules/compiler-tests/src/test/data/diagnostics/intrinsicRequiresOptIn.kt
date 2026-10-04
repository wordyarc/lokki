// RUN_PIPELINE_TILL: FRONTEND

import io.lokki.InternalLokkiApi
import io.lokki.internal.LokkiIntrinsic

@<!OPT_IN_USAGE_ERROR!>LokkiIntrinsic<!>(<!OPT_IN_USAGE_ERROR!>LokkiIntrinsic<!>.<!OPT_IN_USAGE_ERROR!>Kind<!>.<!OPT_IN_USAGE_ERROR!>MOCK<!>)
fun <T> customMock(): T = TODO()

@OptIn(InternalLokkiApi::class)
@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK)
fun <T> optedInMock(): T = TODO()
