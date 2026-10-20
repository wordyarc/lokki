// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.mock
import java.util.concurrent.TimeUnit

abstract class KafkaFuture<T> {
    abstract operator fun get(
        timeout: Long,
        unit: TimeUnit,
    ): T
}

fun test() {
    val kafkaFuture: KafkaFuture<Void> = mock()
    every { kafkaFuture[any(), any()] } returns <!LOKKI_TYPE_NOT_MOCKABLE!>mock()<!>
    kafkaFuture[10, TimeUnit.MILLISECONDS]
}
