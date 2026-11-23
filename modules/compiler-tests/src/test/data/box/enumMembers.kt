// MODULE: main
// MOCKABLE: sample.**
// FILE: enums.kt
package sample

enum class Lvl(val weight: Int) {
    LOW(1),
    HIGH(2);

    fun doubled() = weight * 2

    fun label() = name.lowercase()

    private fun secret() = "real"

    fun revealed() = secret()
}

enum class Op {
    ADD {
        override fun apply(a: Int, b: Int) = a + b

        override fun title() = "add:" + super.title()
    },
    MUL {
        override fun apply(a: Int, b: Int) = a * b
    };

    abstract fun apply(a: Int, b: Int): Int

    open fun title() = "op"

    fun twice(a: Int) = apply(a, a)
}

enum class Coded {
    ONE {
        override val code = 1

        override var note = "one"
    },
    TWO {
        override val code: Int
            get() = 2

        override var note = "two"
    };

    abstract val code: Int

    abstract var note: String

    fun codeTwice() = code * 2
}

enum class Plain {
    ONLY;

    companion object {
        fun first() = ONLY
    }
}

fun labelOf(level: Lvl) = level.label()

// MODULE: test(main)
// FILE: test.kt
import io.lokki.clearMocks
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.isMock
import io.lokki.lokkiScope
import io.lokki.mockObject
import io.lokki.unmockObject
import io.lokki.verify
import sample.Coded
import sample.Lvl
import sample.Op
import sample.Plain
import sample.labelOf

fun box(): String = lokkiScope {
    mockObject(Lvl.HIGH)
    if (!isMock(Lvl.HIGH, regular = false, objectMock = true)) return@lokkiScope "isMock"
    if (isMock(Lvl.LOW, regular = false, objectMock = true)) return@lokkiScope "other entry is not a mock"
    every { Lvl.HIGH.label() } returns "stub"
    if (Lvl.HIGH.label() != "stub") return@lokkiScope "own member"
    if (labelOf(Lvl.HIGH) != "stub") return@lokkiScope "call from main"
    if (Lvl.LOW.label() != "low") return@lokkiScope "other entry"
    verify(exactly = 2) { Lvl.HIGH.label() }
    every { Lvl.HIGH.weight } answers { fieldValue + 1 }
    if (Lvl.HIGH.weight != 3) return@lokkiScope "fieldValue of an entry"
    every { Lvl.HIGH.weight } returns 5
    if (Lvl.HIGH.weight != 5 || Lvl.LOW.weight != 1) return@lokkiScope "property"
    if (Lvl.HIGH.doubled() != 10) return@lokkiScope "read inside the enum"
    every { Lvl.HIGH["secret"]() } returns "hidden"
    if (Lvl.HIGH.revealed() != "hidden") return@lokkiScope "private member"
    clearMocks(Lvl.HIGH)
    if (Lvl.HIGH.label() != "high") return@lokkiScope "original after clearMocks"
    verify { Lvl.HIGH.label() }
    confirmVerified(Lvl.HIGH)
    unmockObject(Lvl.HIGH)
    if (isMock(Lvl.HIGH, regular = false, objectMock = true)) return@lokkiScope "unmocked"

    mockObject(Op.ADD) {
        if (Op.ADD.apply(1, 2) != 3) return@lokkiScope "original of an entry"
        every { Op.ADD.apply(1, 2) } returns 42
        if (Op.ADD.apply(1, 2) != 42) return@lokkiScope "abstract member"
        if (Op.MUL.apply(1, 2) != 2) return@lokkiScope "other entry of abstract"
        every { Op.ADD.apply(2, 2) } returns 9
        if (Op.ADD.twice(2) != 9) return@lokkiScope "call between members"
        if (Op.ADD.title() != "add:op") return@lokkiScope "super call: ${Op.ADD.title()}"
        every { Op.ADD.title() } returns "t"
        if (Op.ADD.title() != "t" || Op.MUL.title() != "op") return@lokkiScope "open member"
    }
    if (Op.ADD.apply(1, 2) != 3) return@lokkiScope "after the block"
    mockObject(Coded.ONE) {
        if (Coded.ONE.code != 1 || Coded.TWO.code != 2) return@lokkiScope "abstract property original"
        every { Coded.ONE.code } returns 50
        if (Coded.ONE.code != 50 || Coded.ONE.codeTwice() != 100) return@lokkiScope "abstract property"
        if (Coded.TWO.codeTwice() != 4) return@lokkiScope "other entry of property"
        Coded.ONE.note = "changed"
        if (Coded.ONE.note != "changed" || Coded.TWO.note != "two") return@lokkiScope "setter original"
        every { Coded.ONE.note = any() } answers { callOriginal() }
        Coded.ONE.note = "x"
        if (Coded.ONE.note != "x") return@lokkiScope "setter through callOriginal: ${Coded.ONE.note}"
    }
    if (Plain.first() != Plain.ONLY || Plain.valueOf("ONLY") != Plain.ONLY || Plain.entries.size != 1) {
        return@lokkiScope "special members"
    }
    val entry = Lvl.LOW
    every { entry.label() } returns "auto"
    if (Lvl.LOW.label() != "auto") return@lokkiScope "every installs the redirect"
    "OK"
}
