// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.mock
import io.lokki.verify

@JvmInline
value class DummyValue(val value: Int)

open class DummyService {
    open fun argValueClassReturnValueClass(valueClass: DummyValue): DummyValue = DummyValue(0)
}

fun test() {
    val dummyValueClassArg = DummyValue(101)
    val dummyValueClassReturn = DummyValue(202)
    val mock =
        mock<DummyService> {
            every { argValueClassReturnValueClass(<!LOKKI_REF_EQ_OF_VALUE_CLASS!>refEq<!>(dummyValueClassArg)) } returns dummyValueClassReturn
        }

    mock.argValueClassReturnValueClass(dummyValueClassArg)

    verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
}
