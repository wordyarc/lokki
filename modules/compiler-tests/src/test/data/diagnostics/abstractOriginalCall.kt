// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.AnswerScope
import io.lokki.Runs
import io.lokki.coEvery
import io.lokki.every
import io.lokki.just
import io.lokki.mock

interface Pump {
    fun pressure(): Int

    suspend fun fetch(): Int

    fun reset()

    var level: Int

    fun name(): String = "pump"
}

abstract class Engine {
    abstract fun start(): String

    open fun stop(): String = "stopped"
}

abstract class Diesel : Engine()

fun AnswerScope<Int>.original(): Int = callOriginal()

fun test() {
    val pump = mock<Pump>()
    every { pump.pressure() } answers { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() + 1 }
    every { pump.pressure() } returns 1 andThenAnswer { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() }
    every { pump.reset() } just Runs andThenAnswer { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() }
    every { pump.level } answers { listOf(1).sumOf { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() + it } }
    every { pump.level = any() } answers { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() }
    every { pump.name() } answers { callOriginal() }
    coEvery { pump.fetch() } coAnswers { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() }
    every { pump.pressure() } coAnswers { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() }
    every { pump.name() } coAnswers { callOriginal() }
    every { pump.pressure() } answers { original() }
    val stub = every { pump.pressure() }
    stub answers { callOriginal() }

    val diesel = mock<Diesel>()
    every { diesel.start() } answers { "${<!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>()}!" }
    every { diesel.stop() } answers { callOriginal() }
}
