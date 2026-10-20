// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// WITH_KOTLINX_COROUTINES

package sample

import io.lokki.AnswerScope
import io.lokki.Awaits
import io.lokki.Runs
import io.lokki.andThenJust
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
    coEvery { pump.fetch() } coAnswers { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>coCallOriginal<!>() }
    coEvery { pump.fetch() } returns 1 coAndThen { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>coCallOriginal<!>() }
    coEvery { pump.fetch() } returns 1 coAndThen { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>() }
    coEvery { pump.fetch() } just Awaits coAndThen { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>coCallOriginal<!>() }
    coEvery { pump.fetch() } returns 1 andThenJust Awaits coAndThen { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>coCallOriginal<!>() }
    every { pump.pressure() } coAnswers { <!LOKKI_ABSTRACT_ORIGINAL_CALL!>coCallOriginal<!>() }
    every { pump.name() } coAnswers { coCallOriginal() }
    every { pump.pressure() } answers { original() }
    val stub = every { pump.pressure() }
    stub answers { callOriginal() }

    val diesel = mock<Diesel>()
    every { diesel.start() } answers { "${<!LOKKI_ABSTRACT_ORIGINAL_CALL!>callOriginal<!>()}!" }
    every { diesel.stop() } answers { callOriginal() }
}
