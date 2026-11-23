// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
import io.lokki.every
import io.lokki.mock

open class Holder {
    open var custom: Int = 1
        get() = field * 10
        set(value) {
            field = value + 1
        }

    open val computed: Int
        get() = 1

    open var plain: Int = 1

    open fun function(): Int = 1
}

fun test(holder: Holder) {
    every { holder.custom } answers { <!LOKKI_FIELD_VALUE_UNAVAILABLE!>fieldValue<!> }
    every { holder.custom = 1 } propertyType Int::class answers { <!LOKKI_FIELD_VALUE_UNAVAILABLE!>fieldValue<!> = value }
    every { holder.computed } answers { <!LOKKI_FIELD_VALUE_UNAVAILABLE!>fieldValueAny<!> as Int }
    every { holder.function() } answers { <!LOKKI_FIELD_VALUE_UNAVAILABLE!>fieldValue<!> }
    every { holder.plain } answers { fieldValue }
}
